package com.jzqs.app.menu.service.impl;

import com.jzqs.app.admin.persistence.AdminRowMappers;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.api.MenuScheduleResponse;
import com.jzqs.app.menu.api.MenuScheduleStatusResponse;
import com.jzqs.app.menu.api.MenuScheduleUpsertResponse;
import com.jzqs.app.menu.service.MenuScheduleService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuScheduleServiceImpl implements MenuScheduleService {
    private final JdbcTemplate jdbcTemplate;

    public MenuScheduleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<MenuScheduleResponse> list() {
        String sql = """
            SELECT
                mwi.id,
                mwi.serve_date AS serve_date,
                meal_period,
                COALESCE(meal_name, '') AS meal_name,
                COALESCE(meal_detail, '') AS meal_detail,
                COALESCE(calories, 0) AS calories,
                COALESCE(merchant_note, '-') AS merchant_note,
                slot_status AS status
            FROM menu_week_items mwi
            JOIN menu_weeks mw ON mw.id = mwi.week_id
            WHERE mw.status = 'PUBLISHED'
            ORDER BY serve_date, CASE WHEN meal_period = 'LUNCH' THEN 1 ELSE 2 END, mwi.id
            """;
        List<MenuScheduleResponse> items = jdbcTemplate.query(sql, AdminRowMappers.MENU_SCHEDULE);
        return PageResponse.of(items, 1, 20, items.size());
    }

    @Override
    @Transactional
    public MenuScheduleUpsertResponse create(
        String serveDate,
        String mealPeriod,
        String mealName,
        String mealDetail,
        int calories,
        String merchantNote
    ) {
        Long weekId = jdbcTemplate.query(
            "SELECT id FROM menu_weeks WHERE status = 'DRAFT' ORDER BY week_start_date ASC LIMIT 1",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (weekId == null) {
            weekId = jdbcTemplate.query(
                "SELECT id FROM menu_weeks WHERE status = 'PUBLISHED' ORDER BY week_start_date ASC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (week_id, serve_date, weekday_index, meal_period, slot_status, meal_name, meal_detail, calories, merchant_note, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            weekId,
            LocalDate.parse(serveDate),
            LocalDate.parse(serveDate).getDayOfWeek().getValue(),
            mealPeriod,
            "ACTIVE",
            mealName,
            mealDetail,
            calories,
            merchantNote,
            "LUNCH".equals(mealPeriod) ? 1 : 2
        );
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM menu_week_items", Long.class);
        return new MenuScheduleUpsertResponse(
            id == null ? 0L : id,
            serveDate,
            mealPeriod,
            mealName,
            mealDetail,
            calories,
            merchantNote,
            "ACTIVE"
        );
    }

    @Override
    @Transactional
    public MenuScheduleUpsertResponse update(
        long id,
        String serveDate,
        String mealPeriod,
        String mealName,
        String mealDetail,
        int calories,
        String merchantNote
    ) {
        int updated = jdbcTemplate.update(
            "UPDATE menu_week_items SET serve_date = ?, meal_period = ?, meal_name = ?, meal_detail = ?, calories = ?, merchant_note = ?, slot_status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            LocalDate.parse(serveDate),
            mealPeriod,
            mealName,
            mealDetail,
            calories,
            merchantNote,
            id
        );
        if (updated == 0) {
            return new MenuScheduleUpsertResponse(id, serveDate, mealPeriod, mealName, mealDetail, calories, merchantNote, "NOT_FOUND");
        }
        String status = jdbcTemplate.queryForObject("SELECT slot_status FROM menu_week_items WHERE id = ?", String.class, id);
        return new MenuScheduleUpsertResponse(
            id,
            serveDate,
            mealPeriod,
            mealName,
            mealDetail,
            calories,
            merchantNote,
            status
        );
    }

    @Override
    @Transactional
    public MenuScheduleStatusResponse disable(long id) {
        int updated = jdbcTemplate.update(
            "UPDATE menu_week_items SET slot_status = 'REST', meal_name = NULL, meal_detail = NULL, calories = NULL, merchant_note = NULL, image_url = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            id
        );
        if (updated == 0) {
            return new MenuScheduleStatusResponse(id, "NOT_FOUND");
        }
        return new MenuScheduleStatusResponse(id, "DISABLED");
    }
}
