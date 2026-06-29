package com.jzqs.app.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.menu.api.MenuWeekPublishResponse;
import com.jzqs.app.menu.api.MenuWeekDaySaveRequest;
import com.jzqs.app.menu.api.MenuWeekDaySlotRequest;
import com.jzqs.app.menu.api.MenuWeekAdminResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MenuWeekAdminServiceTest {

    @Autowired
    private MenuWeekAdminService menuWeekAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetWeeklyMenus() {
        jdbcTemplate.update("DELETE FROM menu_week_items");
        jdbcTemplate.update("DELETE FROM menu_weeks");
        insertWeek(10L, LocalDate.now().minusWeeks(3), "ARCHIVED");
        insertWeek(11L, LocalDate.now().minusWeeks(2), "ARCHIVED");
        insertWeek(12L, LocalDate.now().minusWeeks(1), "ARCHIVED");
        insertWeek(13L, currentMonday(LocalDate.now()), "PUBLISHED");
    }

    @Test
    void shouldReturnCurrentNaturalWeekByDefault() {
        MenuWeekAdminResponse response = menuWeekAdminService.currentWeek();

        assertEquals(currentMonday(LocalDate.now()).toString(), response.weekStartDate());
        assertEquals(currentMonday(LocalDate.now()).plusDays(6).toString(), response.weekEndDate());
        assertEquals("PUBLISHED", response.status());
    }

    @Test
    void shouldCreateSelectedFutureWeekAndDeleteOlderThanTwoWeeks() {
        String targetDate = currentMonday(LocalDate.now()).plusWeeks(1).plusDays(2).toString();

        MenuWeekAdminResponse response = menuWeekAdminService.weekByDate(targetDate);

        assertEquals(currentMonday(LocalDate.parse(targetDate)).toString(), response.weekStartDate());
        Integer oldWeekCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_weeks WHERE week_start_date < ?",
            Integer.class,
            currentMonday(LocalDate.now()).minusWeeks(2)
        );
        assertEquals(0, oldWeekCount == null ? 0 : oldWeekCount);
    }

    @Test
    void shouldRejectDeletedHistoricalWeekSelection() {
        String targetDate = currentMonday(LocalDate.now()).minusWeeks(3).plusDays(1).toString();

        assertThrows(BusinessException.class, () -> menuWeekAdminService.weekByDate(targetDate));
    }

    @Test
    void shouldPersistDishItemsWhenSavingDay() {
        String serveDate = currentMonday(LocalDate.now()).toString();

        menuWeekAdminService.saveDay(
            13L,
            serveDate,
            new MenuWeekDaySaveRequest(
                new MenuWeekDaySlotRequest(
                    "ACTIVE",
                    List.of("测试菜品A", "清炒时蔬"),
                    480,
                    "少油",
                    ""
                ),
                new MenuWeekDaySlotRequest(
                    "REST",
                    List.of(),
                    null,
                    "",
                    ""
                )
            )
        );

        Map<String, Object> lunch = jdbcTemplate.queryForMap(
            "SELECT slot_status, dish_items_json, meal_name, merchant_note FROM menu_week_items WHERE week_id = ? AND serve_date = ? AND meal_period = 'LUNCH'",
            13L,
            LocalDate.parse(serveDate)
        );
        assertEquals("ACTIVE", lunch.get("slot_status"));
        assertEquals("测试菜品A+清炒时蔬", lunch.get("meal_name"));
        assertEquals("少油", lunch.get("merchant_note"));
        assertTrue(String.valueOf(lunch.get("dish_items_json")).contains("测试菜品A"));
        assertTrue(String.valueOf(lunch.get("dish_items_json")).contains("清炒时蔬"));

        Map<String, Object> dinner = jdbcTemplate.queryForMap(
            "SELECT slot_status, meal_name FROM menu_week_items WHERE week_id = ? AND serve_date = ? AND meal_period = 'DINNER'",
            13L,
            LocalDate.parse(serveDate)
        );
        assertEquals("REST", dinner.get("slot_status"));
        assertEquals(null, dinner.get("meal_name"));
    }

    @Test
    void shouldPublishDraftWeekAndSetPublishedMetadata() {
        LocalDate nextWeekStart = currentMonday(LocalDate.now()).plusWeeks(1);
        insertWeek(14L, nextWeekStart, "DRAFT");

        MenuWeekPublishResponse result = menuWeekAdminService.publish(14L, "老板");

        assertEquals("PUBLISHED", result.status());
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject("SELECT status FROM menu_weeks WHERE id = ?", String.class, 14L));
        assertEquals("老板", jdbcTemplate.queryForObject("SELECT published_by FROM menu_weeks WHERE id = ?", String.class, 14L));
        assertEquals("PUBLISHED", jdbcTemplate.queryForObject("SELECT status FROM menu_weeks WHERE id = ?", String.class, 13L));
    }

    private void insertWeek(long id, LocalDate weekStartDate, String status) {
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, created_by, published_by, published_at) VALUES (?, ?, ?, ?, 'test', 'test', CURRENT_TIMESTAMP)",
            id,
            weekStartDate,
            weekEndDate,
            status
        );
        for (int i = 0; i < 7; i++) {
            LocalDate serveDate = weekStartDate.plusDays(i);
            String slotStatus = i == 6 ? "REST" : "UNCONFIGURED";
            jdbcTemplate.update(
                "INSERT INTO menu_week_items (week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, sort_order) VALUES (?, ?, ?, 'LUNCH', ?, '[]', NULL, 1)",
                id,
                serveDate,
                i + 1,
                slotStatus
            );
            jdbcTemplate.update(
                "INSERT INTO menu_week_items (week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json, total_calories, sort_order) VALUES (?, ?, ?, 'DINNER', ?, '[]', NULL, 2)",
                id,
                serveDate,
                i + 1,
                slotStatus
            );
        }
    }

    private LocalDate currentMonday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
}
