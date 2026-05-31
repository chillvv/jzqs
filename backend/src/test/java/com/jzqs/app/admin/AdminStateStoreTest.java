package com.jzqs.app.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.dispatch.service.DispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AdminPersistenceDispatchTest {
    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, DATE '2026-05-12', 'MINIAPP', 'DELIVERED', FALSE, TIMESTAMP '2026-05-10 09:00:00')");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, DATE '2026-05-12', 'MINIAPP', 'PENDING_DISPATCH', FALSE, TIMESTAMP '2026-05-10 09:05:00')");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, DATE '2026-05-12', 'BACKEND', 'DISPATCHING', FALSE, TIMESTAMP '2026-05-10 09:10:00')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (1, 1, 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (2, 2, 'DINNER', 1, 2, '-', 'PENDING_DISPATCH')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status) VALUES (3, 3, 'LUNCH', 1, 3, '微辣', 'DISPATCHING')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')");
    }

    @Test
    void autoAssignShouldCreateNewDispatchRowsForAllPendingOrders() {
        int dispatchCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments", Integer.class);

        dispatchService.autoAssignPendingOrders();

        int dispatchCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments", Integer.class);

        assertEquals(dispatchCountBefore + 1, dispatchCountAfter);
    }
}
