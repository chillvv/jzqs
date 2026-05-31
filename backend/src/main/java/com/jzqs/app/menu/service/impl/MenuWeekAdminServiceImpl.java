package com.jzqs.app.menu.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.menu.api.MenuWeekAdminResponse;
import com.jzqs.app.menu.api.MenuWeekDayResponse;
import com.jzqs.app.menu.api.MenuWeekDaySaveRequest;
import com.jzqs.app.menu.api.MenuWeekDaySlotRequest;
import com.jzqs.app.menu.api.MenuWeekDaySlotResponse;
import com.jzqs.app.menu.service.MenuWeekAdminService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuWeekAdminServiceImpl implements MenuWeekAdminService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MenuWeekAdminServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MenuWeekAdminResponse currentWeek() {
        return loadOrCreateWeek(currentWeekMonday(LocalDate.now()));
    }

    @Override
    public MenuWeekAdminResponse weekByDate(String targetDate) {
        LocalDate targetWeekStart = currentWeekMonday(LocalDate.parse(targetDate));
        LocalDate oldestKeepWeekStart = currentWeekMonday(LocalDate.now()).minusWeeks(2);
        if (targetWeekStart.isBefore(oldestKeepWeekStart)) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "仅支持查看近两周和未来周菜单");
        }
        return loadOrCreateWeek(targetWeekStart);
    }

    @Override
    @Transactional
    public Map<String, Object> createNextWeekTemplate(String operatorName) {
        LocalDate nextWeekStart = currentWeekMonday(LocalDate.now()).plusWeeks(1);
        cleanupExpiredWeeks(currentWeekMonday(LocalDate.now()));
        Long existingId = findWeekIdByStartDate(nextWeekStart);
        if (existingId == null) {
            existingId = createWeekRecord(nextWeekStart, operatorName);
        }
        return Map.of(
            "weekId", existingId,
            "weekStartDate", nextWeekStart.toString(),
            "weekEndDate", nextWeekStart.plusDays(6).toString(),
            "status", "DRAFT"
        );
    }

    @Override
    @Transactional
    public Map<String, Object> saveDay(long weekId, String serveDate, MenuWeekDaySaveRequest request) {
        LocalDate targetDate = LocalDate.parse(serveDate);
        ensureDayBelongsToWeek(weekId, targetDate);
        saveSlot(weekId, targetDate, "LUNCH", request.lunch());
        saveSlot(weekId, targetDate, "DINNER", request.dinner());
        jdbcTemplate.update("UPDATE menu_weeks SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", weekId);
        return Map.of(
            "weekId", weekId,
            "serveDate", serveDate,
            "status", "SAVED"
        );
    }

    @Override
    @Transactional
    public Map<String, Object> copyFromLastWeek(String operatorName) {
        LocalDate thisWeekStart = currentWeekMonday(LocalDate.now());
        LocalDate lastWeekStart = thisWeekStart.minusWeeks(1);

        // 找上周的 weekId（任意状态）
        Long sourceWeekId = jdbcTemplate.query(
            "SELECT id FROM menu_weeks WHERE week_start_date = ? ORDER BY CASE WHEN status = 'PUBLISHED' THEN 0 ELSE 1 END, id DESC LIMIT 1",
            ps -> ps.setObject(1, lastWeekStart),
            rs -> rs.next() ? rs.getLong("id") : null
        );
        if (sourceWeekId == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "上周没有可复制的菜单数据");
        }

        // 确保这周存在（不存在则创建空模板）
        cleanupExpiredWeeks(thisWeekStart);
        Long targetWeekId = findWeekIdByStartDate(thisWeekStart);
        if (targetWeekId == null) {
            targetWeekId = createWeekRecord(thisWeekStart, operatorName);
        }

        // 读取上周所有餐槽数据
        List<Map<String, Object>> sourceSlots = jdbcTemplate.query(
            """
                SELECT serve_date, meal_period, slot_status, dish_items_json, total_calories, merchant_note, image_url
                FROM menu_week_items
                WHERE week_id = ?
                ORDER BY serve_date, CASE WHEN meal_period = 'LUNCH' THEN 1 ELSE 2 END
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serve_date", rs.getDate("serve_date").toLocalDate());
                row.put("meal_period", rs.getString("meal_period"));
                row.put("slot_status", rs.getString("slot_status"));
                row.put("dish_items_json", rs.getString("dish_items_json"));
                row.put("total_calories", rs.getObject("total_calories"));
                row.put("merchant_note", rs.getString("merchant_note"));
                row.put("image_url", rs.getString("image_url"));
                return row;
            },
            sourceWeekId
        );

        // 将上周每天的数据映射到这周对应的星期几
        final long finalTargetWeekId = targetWeekId;
        for (Map<String, Object> slot : sourceSlots) {
            LocalDate sourceDate = (LocalDate) slot.get("serve_date");
            // 保持星期几不变，只换到这周
            LocalDate targetDate = thisWeekStart.plusDays(sourceDate.getDayOfWeek().getValue() - 1L);
            String mealPeriod = (String) slot.get("meal_period");
            String slotStatus = (String) slot.get("slot_status");
            String dishItemsJson = (String) slot.get("dish_items_json");
            Object totalCalories = slot.get("total_calories");
            String merchantNote = (String) slot.get("merchant_note");
            String imageUrl = (String) slot.get("image_url");

            // 读取菜品列表，重新生成 meal_name/meal_detail
            List<String> dishItems = readDishItems(dishItemsJson);
            String joinedDishItems = dishItems.isEmpty() ? null : String.join("+", dishItems);

            jdbcTemplate.update(
                """
                    UPDATE menu_week_items
                    SET slot_status = ?, dish_items_json = ?, total_calories = ?, meal_name = ?, meal_detail = ?, calories = ?, merchant_note = ?, image_url = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE week_id = ? AND serve_date = ? AND meal_period = ?
                    """,
                slotStatus,
                dishItemsJson,
                totalCalories,
                joinedDishItems,
                joinedDishItems,
                totalCalories,
                merchantNote,
                imageUrl,
                finalTargetWeekId,
                targetDate,
                mealPeriod
            );
        }

        jdbcTemplate.update("UPDATE menu_weeks SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", targetWeekId);

        return Map.of(
            "weekId", targetWeekId,
            "weekStartDate", thisWeekStart.toString(),
            "weekEndDate", thisWeekStart.plusDays(6).toString(),
            "status", "DRAFT",
            "copiedFromWeekStart", lastWeekStart.toString()
        );
    }

    @Override
    @Transactional
    public Map<String, Object> publish(long weekId, String operatorName) {
        LocalDate weekStart = jdbcTemplate.query(
            "SELECT week_start_date FROM menu_weeks WHERE id = ?",
            ps -> ps.setLong(1, weekId),
            rs -> rs.next() ? rs.getDate(1).toLocalDate() : null
        );
        if (weekStart == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "未找到对应周菜单");
        }
        jdbcTemplate.update(
            "UPDATE menu_weeks SET status = 'ARCHIVED' WHERE week_start_date = ? AND status = 'PUBLISHED'",
            weekStart
        );
        jdbcTemplate.update(
            "UPDATE menu_weeks SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, published_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            operatorName, weekId
        );
        return Map.of("weekId", weekId, "status", "PUBLISHED");
    }

    private void createDefaultSlots(long weekId, LocalDate weekStart) {
        for (int i = 0; i < 7; i++) {
            LocalDate serveDate = weekStart.plusDays(i);
            String defaultStatus = serveDate.getDayOfWeek() == DayOfWeek.SUNDAY ? "REST" : "UNCONFIGURED";
            insertSlot(weekId, serveDate, i + 1, "LUNCH", defaultStatus, 1);
            insertSlot(weekId, serveDate, i + 1, "DINNER", defaultStatus, 2);
        }
    }

    private MenuWeekAdminResponse loadOrCreateWeek(LocalDate weekStart) {
        cleanupExpiredWeeks(currentWeekMonday(LocalDate.now()));
        Long weekId = findWeekIdByStartDate(weekStart);
        if (weekId == null) {
            weekId = createWeekRecord(weekStart, "system");
        }
        Map<String, Object> week = findWeekById(weekId);
        List<MenuWeekDayResponse> days = loadWeekDays(weekId);
        return new MenuWeekAdminResponse(
            weekId,
            String.valueOf(week.get("week_start_date")),
            String.valueOf(week.get("week_end_date")),
            String.valueOf(week.get("status")),
            days
        );
    }

    private Long createWeekRecord(LocalDate weekStart, String operatorName) {
        LocalDate weekEnd = weekStart.plusDays(6);
        String status = weekStart.equals(currentWeekMonday(LocalDate.now())) ? "PUBLISHED" : "DRAFT";
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (week_start_date, week_end_date, status, created_by, published_by, published_at) VALUES (?, ?, ?, ?, ?, ?)",
            weekStart,
            weekEnd,
            status,
            operatorName,
            "PUBLISHED".equals(status) ? operatorName : null,
            "PUBLISHED".equals(status) ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null
        );
        Long weekId = findWeekIdByStartDate(weekStart);
        createDefaultSlots(weekId == null ? 0L : weekId, weekStart);
        return weekId == null ? 0L : weekId;
    }

    private Long findWeekIdByStartDate(LocalDate weekStart) {
        return jdbcTemplate.query(
            "SELECT id FROM menu_weeks WHERE week_start_date = ? ORDER BY CASE WHEN status = 'PUBLISHED' THEN 0 ELSE 1 END, id DESC LIMIT 1",
            ps -> ps.setObject(1, weekStart),
            rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    private Map<String, Object> findWeekById(long weekId) {
        Map<String, Object> week = jdbcTemplate.query(
            """
                SELECT id, week_start_date, week_end_date, status
                FROM menu_weeks
                WHERE id = ?
                """,
            ps -> ps.setLong(1, weekId),
            rs -> rs.next() ? Map.of(
                "id", rs.getLong("id"),
                "week_start_date", rs.getDate("week_start_date").toLocalDate().toString(),
                "week_end_date", rs.getDate("week_end_date").toLocalDate().toString(),
                "status", rs.getString("status")
            ) : null
        );
        if (week == null) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "未找到对应周菜单");
        }
        return week;
    }

    private void cleanupExpiredWeeks(LocalDate currentWeekStart) {
        LocalDate oldestKeepWeekStart = currentWeekStart.minusWeeks(2);
        jdbcTemplate.update(
            "DELETE FROM menu_week_items WHERE week_id IN (SELECT id FROM menu_weeks WHERE week_start_date < ?)",
            oldestKeepWeekStart
        );
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE week_start_date < ?", oldestKeepWeekStart);
    }

    private void insertSlot(long weekId, LocalDate serveDate, int weekdayIndex, String mealPeriod, String slotStatus, int sortOrder) {
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (week_id, serve_date, weekday_index, meal_period, slot_status, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            weekId, serveDate, weekdayIndex, mealPeriod, slotStatus, sortOrder
        );
    }

    private void saveSlot(long weekId, LocalDate serveDate, String mealPeriod, MenuWeekDaySlotRequest slot) {
        String slotStatus = normalize(slot.slotStatus(), "UNCONFIGURED");
        List<String> dishItems = normalizeDishItems(slotStatus, slot.dishItems());
        Integer totalCalories = "ACTIVE".equals(slotStatus) ? slot.totalCalories() : null;
        String joinedDishItems = dishItems.isEmpty() ? null : String.join("+", dishItems);
        jdbcTemplate.update(
            """
                UPDATE menu_week_items
                SET slot_status = ?, dish_items_json = ?, total_calories = ?, meal_name = ?, meal_detail = ?, calories = ?, merchant_note = ?, image_url = ?, updated_at = CURRENT_TIMESTAMP
                WHERE week_id = ? AND serve_date = ? AND meal_period = ?
                """,
            slotStatus,
            writeDishItems(dishItems),
            totalCalories,
            joinedDishItems,
            joinedDishItems,
            totalCalories,
            normalizeNullable(slotStatus, slot.merchantNote()),
            normalizeNullable(slotStatus, slot.imageUrl()),
            weekId,
            serveDate,
            mealPeriod
        );
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private List<String> normalizeDishItems(String slotStatus, List<String> dishItems) {
        if (!"ACTIVE".equals(slotStatus) || dishItems == null) {
            return List.of();
        }
        return dishItems.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .toList();
    }

    private String normalizeNullable(String slotStatus, String value) {
        if (!"ACTIVE".equals(slotStatus)) {
            return null;
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureDayBelongsToWeek(long weekId, LocalDate serveDate) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_week_items WHERE week_id = ? AND serve_date = ?",
            Integer.class,
            weekId,
            serveDate
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND, "该日期不属于当前周菜单");
        }
    }

    private List<MenuWeekDayResponse> loadWeekDays(long weekId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
                SELECT
                    serve_date,
                    meal_period,
                    slot_status,
                    COALESCE(dish_items_json, '[]') AS dish_items_json,
                    total_calories,
                    COALESCE(merchant_note, '') AS merchant_note,
                    COALESCE(image_url, '') AS image_url
                FROM menu_week_items
                WHERE week_id = ?
                ORDER BY serve_date, CASE WHEN meal_period = 'LUNCH' THEN 1 ELSE 2 END
                """,
            (rs, rowNum) -> mapWeekRow(rs),
            weekId
        );
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            grouped.computeIfAbsent(String.valueOf(row.get("serve_date")), key -> new ArrayList<>()).add(row);
        }
        List<MenuWeekDayResponse> days = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            MenuWeekDaySlotResponse lunch = null;
            MenuWeekDaySlotResponse dinner = null;
            for (Map<String, Object> row : entry.getValue()) {
                MenuWeekDaySlotResponse slot = new MenuWeekDaySlotResponse(
                    String.valueOf(row.get("meal_period")),
                    String.valueOf(row.get("slot_status")),
                    readDishItems(stringValue(row.get("dish_items_json"))),
                    row.get("total_calories") == null ? null : ((Number) row.get("total_calories")).intValue(),
                    stringValue(row.get("merchant_note")),
                    stringValue(row.get("image_url"))
                );
                if ("LUNCH".equals(row.get("meal_period"))) {
                    lunch = slot;
                } else {
                    dinner = slot;
                }
            }
            LocalDate serveDate = LocalDate.parse(entry.getKey());
            days.add(new MenuWeekDayResponse(
                entry.getKey(),
                weekdayLabel(serveDate),
                lunch,
                dinner
            ));
        }
        return days;
    }

    private Map<String, Object> mapWeekRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serve_date", rs.getDate("serve_date").toLocalDate().toString());
        row.put("meal_period", rs.getString("meal_period"));
        row.put("slot_status", rs.getString("slot_status"));
        row.put("dish_items_json", rs.getString("dish_items_json"));
        row.put("total_calories", rs.getObject("total_calories"));
        row.put("merchant_note", rs.getString("merchant_note"));
        row.put("image_url", rs.getString("image_url"));
        return row;
    }

    private String writeDishItems(List<String> dishItems) {
        try {
            return objectMapper.writeValueAsString(dishItems == null ? List.of() : dishItems);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("菜品列表序列化失败", ex);
        }
    }

    private List<String> readDishItems(String rawJson) {
        try {
            if (rawJson == null || rawJson.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawJson, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("菜品列表解析失败", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String weekdayLabel(LocalDate serveDate) {
        return switch (serveDate.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private LocalDate currentWeekMonday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
}
