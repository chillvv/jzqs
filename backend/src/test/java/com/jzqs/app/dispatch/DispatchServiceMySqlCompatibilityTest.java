package com.jzqs.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DispatchServiceMySqlCompatibilityTest {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDispatchFixtures() {
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM dispatch_reassignments");
        jdbcTemplate.update("DELETE FROM dispatch_batches");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM dispatch_area_bindings");
        jdbcTemplate.update("DELETE FROM rider_address_bindings WHERE customer_id >= 901");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 901");

        insertCustomer(901L, "张先生", "13800000901");
        insertCustomer(902L, "王女士", "13800000902");
        insertCustomer(903L, "李先生", "13800000903");
        insertCustomer(904L, "赵女士", "13800000904");

        insertAddress(901L, 901L, "高新区软件园A座", "高新区");
        insertAddress(902L, 902L, "高新区软件园B座", "高新区");
        insertAddress(903L, 903L, "商务区星光里", "商务区");
        insertAddress(904L, 904L, "高新区云谷C座", "高新区");

        insertDailyOrder(901L, 901L, "DISPATCHING");
        insertDailyOrder(902L, 902L, "PENDING_DISPATCH");
        insertDailyOrder(903L, 903L, "PENDING_DISPATCH");
        insertDailyOrder(904L, 904L, "PENDING_DISPATCH");

        insertMealSlotOrder(901L, 901L, "LUNCH", 901L, "DISPATCHING");
        insertMealSlotOrder(902L, 902L, "LUNCH", 902L, "PENDING_DISPATCH");
        insertMealSlotOrder(903L, 903L, "DINNER", 903L, "PENDING_DISPATCH");
        insertMealSlotOrder(904L, 904L, "LUNCH", 904L, "PENDING_DISPATCH");

        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, current_openid, auth_status, employment_status,
                    default_area_code, assigned_by, first_login_at, last_login_at, created_at
                ) VALUES (
                    901, '骑手小李', '李师傅', '13800000911', 'rider_openid_901', 'ACTIVE', 'ACTIVE',
                    '高新区', '老板', TIMESTAMP '2026-05-15 08:00:00', TIMESTAMP '2026-05-15 11:30:00', CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, current_openid, auth_status, employment_status,
                    default_area_code, assigned_by, first_login_at, last_login_at, created_at
                ) VALUES (
                    902, '骑手小王', '王师傅', '13800000912', 'rider_openid_902', 'ACTIVE', 'ACTIVE',
                    '商务区', '老板', TIMESTAMP '2026-05-15 09:00:00', TIMESTAMP '2026-05-15 12:00:00', CURRENT_TIMESTAMP
                )
                """
        );

        jdbcTemplate.update(
            """
                INSERT INTO dispatch_area_bindings (
                    area_code, keywords, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at
                ) VALUES (
                    '高新区', '高新区,软件园,云谷', 901, NULL, '老板', TIMESTAMP '2026-05-17 10:00:00'
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_area_bindings (
                    area_code, keywords, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at
                ) VALUES (
                    '商务区', '商务区,星光里', 902, NULL, '老板', TIMESTAMP '2026-05-17 10:05:00'
                )
                """
        );

        jdbcTemplate.update(
            """
                INSERT INTO dispatch_assignments (
                    id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number, created_at
                ) VALUES (
                    901, 901, '骑手小李', 901, '高新区', 'DISPATCHING', 1, CURRENT_TIMESTAMP
                )
                """
        );

        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id, address_id, address_fingerprint, area_code, rider_profile_id,
                    manually_confirmed, updated_reason, updated_at
                ) VALUES (
                    902, 902, '高新区软件园B座', '高新区', 901, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id, address_id, address_fingerprint, area_code, rider_profile_id,
                    manually_confirmed, updated_reason, updated_at
                ) VALUES (
                    903, 903, '商务区星光里', '商务区', 902, TRUE, 'AREA_CONFIRMED', CURRENT_TIMESTAMP
                )
                """
        );
    }

    @Test
    void pendingItemsShouldFilterByMealPeriodWithoutAutoAssignSideEffects() {
        List<DispatchPendingItemResponse> lunchPendingItems = dispatchService.pendingItems("LUNCH", null);
        List<DispatchPendingItemResponse> dinnerPendingItems = dispatchService.pendingItems("DINNER", null);

        assertEquals(2, lunchPendingItems.size());
        assertEquals(902L, lunchPendingItems.get(0).orderId());
        assertEquals(904L, lunchPendingItems.get(1).orderId());
        assertEquals(1, dinnerPendingItems.size());
        assertEquals(903L, dinnerPendingItems.get(0).orderId());

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 902",
                Integer.class
            )
        );
    }

    @Test
    void overviewShouldRespectMealPeriodWithoutAutoAssignment() {
        DispatchOverviewResponse lunchOverview = dispatchService.overview("LUNCH", null);
        DispatchOverviewResponse dinnerOverview = dispatchService.overview("DINNER", null);

        assertEquals(2, lunchOverview.pendingCount());
        assertEquals(1, lunchOverview.dispatchingCount());
        assertEquals(0, lunchOverview.missingRiderAreaCount());

        assertEquals(1, dinnerOverview.pendingCount());
        assertEquals(0, dinnerOverview.dispatchingCount());
        assertEquals(0, dinnerOverview.missingRiderAreaCount());
    }

    @Test
    void autoAssignPendingOrdersShouldSkipInvalidRememberedAreaBindings() {
        jdbcTemplate.update(
            "UPDATE rider_address_bindings SET area_code = '已删除区域', updated_reason = 'AREA_REMOVED' WHERE customer_id = 902 AND address_id = 902"
        );

        dispatchService.autoAssignPendingOrders();

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 902",
                Integer.class
            )
        );
        assertEquals(
            "已删除区域",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 902 AND address_id = 902",
                String.class
            )
        );
    }

    @Test
    void batchAssignPendingOrdersShouldPersistAreaMemoryAndSequence() {
        dispatchService.batchAssignPendingOrders(List.of(904L), "商务区", "老板");

        assertEquals(
            "AREA_ASSIGNED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = 904",
                String.class
            )
        );
        assertNull(
            jdbcTemplate.queryForObject(
                "SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = 904",
                String.class
            )
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT sequence_number FROM dispatch_assignments WHERE meal_slot_order_id = 904",
                Integer.class
            )
        );
        assertEquals(
            "商务区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 904 AND address_id = 904",
                String.class
            )
        );
    }

    @Test
    void reorderAreaOrdersShouldPersistSequenceNumbers() {
        dispatchService.pendingItems("LUNCH", null);
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");

        dispatchService.reorderAreaOrders(
            "高新区",
            List.of(
                new DispatchOrderReorderItemRequest(904L, 1),
                new DispatchOrderReorderItemRequest(901L, 2),
                new DispatchOrderReorderItemRequest(902L, 3)
            )
        );

        assertEquals(1, sequenceOf(904L));
        assertEquals(2, sequenceOf(901L));
        assertEquals(3, sequenceOf(902L));
    }

    @Test
    void moveOrderToAreaShouldUpdateAssignmentAndBinding() {
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");

        dispatchService.moveOrderToArea("高新区", 904L, "商务区", "老板");

        assertEquals(
            "商务区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM dispatch_assignments WHERE meal_slot_order_id = 904",
                String.class
            )
        );
        assertEquals(
            "商务区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 904 AND address_id = 904",
                String.class
            )
        );
    }

    @Test
    void renameAreaShouldSyncRememberedBindings() {
        dispatchService.renameArea("高新区", "科技园片区");

        assertEquals(
            "科技园片区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 902 AND address_id = 902",
                String.class
            )
        );
    }

    @Test
    void deleteAreaShouldClearRememberedBindings() {
        dispatchService.deleteArea("商务区");

        assertNull(
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 903 AND address_id = 903",
                String.class
            )
        );
    }

    @Test
    void assignRiderToAreaShouldDispatchAllAreaAssignedOrders() {
        dispatchService.pendingItems("LUNCH", null);
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");
        dispatchService.batchAssignPendingOrders(List.of(903L), "高新区", "老板");

        dispatchService.assignRiderToArea("高新区", "骑手小李", "老板", "LUNCH");

        assertEquals("DISPATCHING", assignmentStatusOf(902L));
        assertEquals("DISPATCHING", assignmentStatusOf(904L));
        assertEquals("骑手小李", riderNameOf(902L));
        assertEquals("骑手小李", riderNameOf(904L));
        assertEquals(
            "DISPATCHING",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = 904", String.class)
        );
        assertEquals("AREA_ASSIGNED", assignmentStatusOf(903L));
        assertEquals(
            "PENDING_DISPATCH",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = 903", String.class)
        );
    }

    private void insertCustomer(long id, String name, String phone) {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source) VALUES (?, ?, ?, 'BACKEND')",
            id,
            name,
            phone
        );
    }

    private void insertAddress(long id, long customerId, String addressLine, String areaCode) {
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
            id,
            customerId,
            "联系人" + id,
            "1380000" + id,
            addressLine,
            areaCode
        );
    }

    private void insertDailyOrder(long id, long customerId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'MINIAPP', ?, FALSE, CURRENT_TIMESTAMP)
                """,
            id,
            customerId,
            LocalDate.of(2026, 5, 18),
            status
        );
    }

    private void insertMealSlotOrder(long id, long dailyOrderId, String mealPeriod, long addressId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, quantity, address_id, note, status)
                VALUES (?, ?, ?, 1, ?, '-', ?)
                """,
            id,
            dailyOrderId,
            mealPeriod,
            addressId,
            status
        );
    }

    private int sequenceOf(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT sequence_number FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            Integer.class,
            orderId
        );
    }

    private String assignmentStatusOf(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            String.class,
            orderId
        );
    }

    private String riderNameOf(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            String.class,
            orderId
        );
    }
}
