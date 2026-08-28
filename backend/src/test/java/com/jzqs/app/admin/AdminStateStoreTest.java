package com.jzqs.app.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.dispatch.service.DispatchService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AdminStateStoreTest {
    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSeedData() {
        LocalDate serveDate = LocalDate.now().plusDays(1);
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (1, 1, ?, 'MINIAPP', 'DELIVERED', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (2, 2, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (3, 3, ?, 'BACKEND', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)", serveDate);
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (1, 1, 'LUNCH', 'LUNCH', 1, 1, '少饭，不要洋葱', 'DELIVERED', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (2, 2, 'DINNER', 'DINNER', 1, 2, '-', 'PENDING_DISPATCH', 'MINIAPP')");
        jdbcTemplate.update("INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type) VALUES (3, 3, 'LUNCH', 'LUNCH', 1, 3, '微辣', 'DISPATCHING', 'BACKEND')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (1, 1, '骑手老周', '高新区', 'DELIVERED')");
        jdbcTemplate.update("INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status) VALUES (2, 3, '骑手小李', '商务区', 'DISPATCHING')");
    }

    @Test
    void autoAssignShouldCreateNewDispatchRowsForAllPendingOrders() {
        int dispatchCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments", Integer.class);

        dispatchService.autoAssignPendingOrders();

        int dispatchCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dispatch_assignments", Integer.class);

        assertEquals(dispatchCountBefore, dispatchCountAfter);
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 2",
                Integer.class
            )
        );
    }
}
