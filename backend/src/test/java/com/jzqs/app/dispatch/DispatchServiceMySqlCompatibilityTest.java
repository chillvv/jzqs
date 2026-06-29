package com.jzqs.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProgressResponse;
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

    private LocalDate serveDate;

    @BeforeEach
    void resetDispatchFixtures() {
        serveDate = LocalDate.now().plusDays(1);
        jdbcTemplate.update(
            "DELETE FROM delivery_receipts WHERE meal_slot_order_id IN (SELECT mso.id FROM meal_slot_orders mso JOIN daily_orders doo ON doo.id = mso.daily_order_id WHERE doo.serve_date = ?)",
            serveDate
        );
        jdbcTemplate.update("DELETE FROM address_reference_images WHERE customer_address_id >= 901");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items");
        jdbcTemplate.update("DELETE FROM dispatch_reassignments");
        jdbcTemplate.update("DELETE FROM dispatch_batches");
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM dispatch_area_bindings");
        jdbcTemplate.update("DELETE FROM rider_address_bindings WHERE customer_id >= 901");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 901");
        jdbcTemplate.update(
            "DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE serve_date = ?)",
            serveDate
        );
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE serve_date = ?", serveDate);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 901");

        insertCustomer(901L, "调度张先生901", "13800000901");
        insertCustomer(902L, "调度王女士902", "13800000902");
        insertCustomer(903L, "调度李先生903", "13800000903");
        insertCustomer(904L, "调度赵女士904", "13800000904");
        insertCustomer(905L, "调度孙女士905", "13800000905");

        insertAddress(901L, 901L, "高新区软件园A座", "高新区");
        insertAddress(902L, 902L, "高新区软件园B座", "高新区");
        insertAddress(903L, 903L, "商务区星光里", "商务区");
        insertAddress(904L, 904L, "高新区云谷C座", "高新区");
        insertAddress(905L, 905L, "万达公寓D座", "万达商圈");

        insertDailyOrder(901L, 901L, "DISPATCHING");
        insertDailyOrder(902L, 902L, "PENDING_DISPATCH");
        insertDailyOrder(903L, 903L, "PENDING_DISPATCH");
        insertDailyOrder(904L, 904L, "PENDING_DISPATCH");
        insertDailyOrder(905L, 905L, "PENDING_DISPATCH");

        insertMealSlotOrder(901L, 901L, "LUNCH", 901L, "DISPATCHING");
        insertMealSlotOrder(902L, 902L, "LUNCH", 902L, "PENDING_DISPATCH");
        insertMealSlotOrder(903L, 903L, "DINNER", 903L, "PENDING_DISPATCH");
        insertMealSlotOrder(904L, 904L, "LUNCH", 904L, "PENDING_DISPATCH");
        insertMealSlotOrder(905L, 905L, "DINNER", 905L, "PENDING_DISPATCH");

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
        List<DispatchPendingItemResponse> lunchPendingItems = dispatchService.pendingItems("LUNCH", serveDate.toString());
        List<DispatchPendingItemResponse> dinnerPendingItems = dispatchService.pendingItems("DINNER", serveDate.toString());

        assertEquals(1, lunchPendingItems.size());
        assertEquals(904L, lunchPendingItems.get(0).orderId());
        assertEquals(1, dinnerPendingItems.size());
        assertEquals(905L, dinnerPendingItems.get(0).orderId());

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 902",
                Integer.class
            )
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 903",
                Integer.class
            )
        );
    }

    @Test
    void overviewShouldRespectMealPeriodWithoutAutoAssignment() {
        DispatchOverviewResponse lunchOverview = dispatchService.overview("LUNCH", serveDate.toString());
        DispatchOverviewResponse dinnerOverview = dispatchService.overview("DINNER", serveDate.toString());

        assertEquals(1, lunchOverview.pendingCount());
        assertEquals(2, lunchOverview.dispatchingCount());
        assertEquals(0, lunchOverview.missingRiderAreaCount());

        assertEquals(1, dinnerOverview.pendingCount());
        assertEquals(1, dinnerOverview.dispatchingCount());
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
            "DISPATCHING",
            jdbcTemplate.queryForObject(
                "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = 904",
                String.class
            )
        );
        assertEquals(
            "骑手小王",
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
        dispatchService.pendingItems("LUNCH", serveDate.toString());
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
            "高新区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 902 AND address_id = 902",
                String.class
            )
        );
    }

    @Test
    void deleteAreaShouldClearRememberedBindings() {
        dispatchService.deleteArea("商务区");

        assertEquals(
            "商务区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM rider_address_bindings WHERE customer_id = 903 AND address_id = 903",
                String.class
            )
        );
    }

    @Test
    void updateAreaBindingShouldCreateNewAreaRecord() {
        dispatchService.updateAreaBinding("万达商圈", "万达商圈", 901L, null, "老板");

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_area_bindings WHERE area_code = '万达商圈'",
                Integer.class
            )
        );
        assertEquals(
            "万达商圈",
            jdbcTemplate.queryForObject(
                "SELECT keywords FROM dispatch_area_bindings WHERE area_code = '万达商圈'",
                String.class
            )
        );
        assertEquals(
            901L,
            jdbcTemplate.queryForObject(
                "SELECT default_rider_profile_id FROM dispatch_area_bindings WHERE area_code = '万达商圈'",
                Long.class
            )
        );
    }

    @Test
    void createRiderShouldPersistProfileAndDefaultArea() {
        dispatchService.createRider("骑手小周", "骑手小周", "13800000920", "万达商圈", "ACTIVE", "老板");

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rider_profiles WHERE rider_name = '骑手小周' AND phone = '13800000920'",
                Integer.class
            )
        );
        assertEquals(
            "ACTIVE",
            jdbcTemplate.queryForObject(
                "SELECT auth_status FROM rider_profiles WHERE rider_name = '骑手小周'",
                String.class
            )
        );
        assertEquals(
            "万达商圈",
            jdbcTemplate.queryForObject(
                "SELECT default_area_code FROM rider_profiles WHERE rider_name = '骑手小周'",
                String.class
            )
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM dispatch_area_bindings dab
                    JOIN rider_profiles rp ON rp.id = dab.default_rider_profile_id
                    WHERE dab.area_code = '万达商圈' AND rp.rider_name = '骑手小周'
                    """,
                Integer.class
            )
        );
    }

    @Test
    void updateRiderProfileShouldReplaceNameInsteadOfConcatenating() {
        dispatchService.updateRiderProfile(901L, "骑手小李已修改", "骑手小李已修改", "13800000919", "云谷片区", "老板");

        assertEquals(
            "骑手小李已修改",
            jdbcTemplate.queryForObject("SELECT rider_name FROM rider_profiles WHERE id = 901", String.class)
        );
        assertEquals(
            "骑手小李已修改",
            jdbcTemplate.queryForObject("SELECT display_name FROM rider_profiles WHERE id = 901", String.class)
        );
        assertEquals(
            "13800000919",
            jdbcTemplate.queryForObject("SELECT phone FROM rider_profiles WHERE id = 901", String.class)
        );
        assertEquals(
            "云谷片区",
            jdbcTemplate.queryForObject("SELECT default_area_code FROM rider_profiles WHERE id = 901", String.class)
        );
        assertEquals(
            "骑手小李已修改",
            jdbcTemplate.queryForObject(
                "SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = 901",
                String.class
            )
        );
    }

    @Test
    void assignRiderToAreaShouldDispatchAllAreaAssignedOrders() {
        dispatchService.pendingItems("LUNCH", serveDate.toString());
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
        assertEquals("DISPATCHING", assignmentStatusOf(903L));
        assertEquals(
            "DISPATCHING",
            jdbcTemplate.queryForObject("SELECT status FROM meal_slot_orders WHERE id = 903", String.class)
        );
    }

    @Test
    void assignRiderToAreaOrderShouldCreateSequenceNumberForNewDispatchOrder() {
        dispatchService.assignRiderToAreaOrder("高新区", 904L, "骑手小李", "老板");

        assertEquals(1, sequenceOf(904L));
    }

    @Test
    void reassignDispatchShouldSyncAssignmentSequenceWithTargetBatchSequence() {
        dispatchService.assignRiderToAreaOrder("商务区", 904L, "骑手小王", "老板");

        dispatchService.reassignDispatch(
            "ORDER",
            901L,
            "骑手小李",
            "骑手小王",
            "商务区",
            serveDate.toString(),
            "LUNCH",
            true,
            "跨区重派",
            "老板"
        );

        assertEquals("骑手小王", riderNameOf(901L));
        assertEquals(
            "商务区",
            jdbcTemplate.queryForObject(
                "SELECT area_code FROM dispatch_assignments WHERE meal_slot_order_id = 901",
                String.class
            )
        );
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "SELECT current_sequence FROM dispatch_batch_items WHERE meal_slot_order_id = 901",
                Integer.class
            )
        );
        assertEquals(
            jdbcTemplate.queryForObject(
                "SELECT current_sequence FROM dispatch_batch_items WHERE meal_slot_order_id = 901",
                Integer.class
            ),
            sequenceOf(901L)
        );
    }

    @Test
    void reassignDispatchShouldResetTransferredBatchItemStatusToPending() {
        dispatchService.assignRiderToAreaOrder("商务区", 904L, "骑手小王", "老板");
        dispatchService.assignRiderToAreaOrder("高新区", 901L, "骑手小李", "老板");
        jdbcTemplate.update(
            "UPDATE dispatch_batch_items SET item_status = 'DEFERRED' WHERE meal_slot_order_id = 901"
        );

        dispatchService.reassignDispatch(
            "ORDER",
            901L,
            "骑手小李",
            "骑手小王",
            "商务区",
            serveDate.toString(),
            "LUNCH",
            true,
            "跨区重派",
            "老板"
        );

        assertEquals(
            "DEFERRED",
            jdbcTemplate.queryForObject(
                "SELECT item_status FROM dispatch_batch_items WHERE meal_slot_order_id = 901",
                String.class
            )
        );
    }

    @Test
    void riderProgressAndAreaBindingsShouldFallbackToBatchSequenceWhenAssignmentSequenceMissing() {
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET sequence_number = 0 WHERE meal_slot_order_id = 901"
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    901, ?, 'LUNCH', 901, '高新区', 'IN_PROGRESS', 1, 0, 5
                )
                """
            ,
            serveDate
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batch_items (
                    id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                ) VALUES (
                    901, 901, 901, 5, 5, 'CURRENT', FALSE
                )
                """
        );

        List<DispatchRiderProgressResponse> progress = dispatchService.riderProgress("LUNCH", serveDate.toString());
        List<DispatchAreaBindingResponse> bindings = dispatchService.areaBindings("LUNCH", serveDate.toString());

        assertEquals(5, progress.get(0).currentSequenceNumber());
        DispatchAreaBindingResponse gaoxinBinding = bindings.stream()
            .filter(binding -> "高新区".equals(binding.areaCode()))
            .findFirst()
            .orElseThrow();
        assertEquals(5, gaoxinBinding.orders().get(0).sequenceNumber());
    }

    @Test
    void riderProgressAndAreaBindingsShouldExposeProgressAndBothImages() {
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET user_note = '少饭', merchant_remark = '放前台', special_tag = '过敏', note = '少饭' WHERE id = 901"
        );
        jdbcTemplate.update(
            """
                INSERT INTO address_reference_images (
                    customer_address_id, reference_image_url, source_order_id, updated_by_rider_name
                ) VALUES (?, ?, ?, ?)
                """,
            901L,
            "/uploads/reference-901.jpg",
            901L,
            "骑手小李"
        );
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (
                    id, meal_slot_order_id, receipt_url, receipt_note, delivered_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            901L,
            901L,
            "/uploads/receipt-901.jpg",
            "已放前台"
        );

        List<DispatchRiderProgressResponse> progress = dispatchService.riderProgress("LUNCH", serveDate.toString());
        List<DispatchAreaBindingResponse> bindings = dispatchService.areaBindings("LUNCH", serveDate.toString());

        assertEquals(1, progress.size());
        assertEquals("骑手小李", progress.get(0).riderName());
        assertEquals(901L, progress.get(0).currentOrderId());
        assertEquals(1, progress.get(0).currentSequenceNumber());
        assertEquals(0, progress.get(0).exceptionCount());

        assertEquals(2, bindings.size());
        DispatchAreaBindingResponse gaoxinBinding = bindings.stream()
            .filter(binding -> "高新区".equals(binding.areaCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("/uploads/reference-901.jpg", gaoxinBinding.orders().get(0).referenceImageUrl());
        assertEquals("/uploads/receipt-901.jpg", gaoxinBinding.orders().get(0).receiptUrl());
        assertEquals("13800000901", gaoxinBinding.orders().get(0).customerPhone());
        assertEquals(true, gaoxinBinding.orders().get(0).hasAttentionMark());
        assertEquals(List.of("USER_NOTE", "MERCHANT_NOTE"), gaoxinBinding.orders().get(0).attentionSources());
        assertFalse(gaoxinBinding.orders().get(0).attentionLabel().isBlank());
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
            serveDate,
            status
        );
    }

    private void insertMealSlotOrder(long id, long dailyOrderId, String mealPeriod, long addressId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type
                )
                VALUES (?, ?, ?, ?, 1, ?, '-', ?, 'MINIAPP')
                """,
            id,
            dailyOrderId,
            mealPeriod,
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
