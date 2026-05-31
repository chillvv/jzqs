package com.jzqs.app.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.api.MenuScheduleResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MenuScheduleServiceTest {

    @Autowired
    private MenuScheduleService menuScheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPublishedMenu() {
        jdbcTemplate.update("DELETE FROM menu_week_items");
        jdbcTemplate.update("DELETE FROM menu_weeks");
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, created_by, published_by, published_at) VALUES (?, ?, ?, 'PUBLISHED', 'test', 'test', CURRENT_TIMESTAMP)",
            1L,
            LocalDate.of(2026, 5, 11),
            LocalDate.of(2026, 5, 17)
        );
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (id, week_id, serve_date, weekday_index, meal_period, slot_status, meal_name, meal_detail, calories, merchant_note, sort_order)
                VALUES (?, ?, ?, ?, 'LUNCH', 'ACTIVE', ?, ?, ?, ?, ?)
                """,
            101L,
            1L,
            LocalDate.of(2026, 5, 12),
            2,
            "黑椒牛柳饭",
            "黑椒牛柳+西兰花+米饭",
            520,
            "少油",
            1
        );
    }

    @Test
    void shouldListPublishedMenuWithStringServeDate() {
        PageResponse<MenuScheduleResponse> response = menuScheduleService.list();

        assertEquals(1, response.items().size());
        assertEquals("2026-05-12", response.items().get(0).serveDate());
        assertEquals("黑椒牛柳饭", response.items().get(0).mealName());
    }
}
