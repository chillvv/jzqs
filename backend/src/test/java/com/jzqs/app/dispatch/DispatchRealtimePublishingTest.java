package com.jzqs.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchRouteLabSimulateRequest;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@SpringBootTest
class DispatchRealtimePublishingTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DispatchService dispatchService;

    @MockBean
    private TransactionalRealtimePublisher realtimeEventPublisher;

    @MockBean
    private DispatchRouteAiRefineService dispatchRouteAiRefineService;

    @BeforeEach
    void resetDispatchFixtures() {
        clearInvocations(realtimeEventPublisher);

        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM address_reference_images WHERE customer_address_id >= 901");
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

        insertCustomer(901L, "调度张先生901", "13800000901");
        insertCustomer(902L, "调度王女士902", "13800000902");
        insertCustomer(903L, "调度李先生903", "13800000903");
        insertCustomer(904L, "调度赵女士904", "13800000904");

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
    }

    @Test
    void assignOrderShouldPublishAssignmentChangedEventFromService() {
        dispatchService.assignOrder(904L, "骑手小李", "高新区");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.assignment.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(904L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void assignRiderToAreaOrderShouldPublishQueueChangedEventFromService() {
        dispatchService.assignRiderToAreaOrder("高新区", 904L, "骑手小李", "老板");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(904L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void batchAssignPendingOrdersShouldPublishAssignmentChangedEventFromService() {
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.assignment.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals(1, ((Number) event.payload().get("orderId")).intValue());
    }

    @Test
    void assignRiderToAreaShouldPublishQueueChangedEventFromService() {
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");
        clearInvocations(realtimeEventPublisher);

        dispatchService.assignRiderToArea("高新区", "骑手小李", "老板", "LUNCH");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals("骑手小李", event.payload().get("riderName"));
    }

    @Test
    void reorderAreaOrdersShouldPublishQueueChangedEventFromService() {
        dispatchService.assignRiderToAreaOrder("高新区", 904L, "骑手小李", "老板");
        clearInvocations(realtimeEventPublisher);

        dispatchService.reorderAreaOrders(
            "高新区",
            List.of(
                new DispatchOrderReorderItemRequest(904L, 1),
                new DispatchOrderReorderItemRequest(901L, 2)
            )
        );

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals(904L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void moveOrderToAreaShouldPublishAssignmentChangedEventFromService() {
        dispatchService.batchAssignPendingOrders(List.of(904L), "高新区", "老板");
        clearInvocations(realtimeEventPublisher);

        dispatchService.moveOrderToArea("高新区", 904L, "商务区", "老板");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, atLeastOnce()).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getAllValues().stream()
            .filter(e -> "dispatch.assignment.changed".equals(e.eventType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少 dispatch.assignment.changed 事件"));

        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertEquals("商务区", event.payload().get("areaCode"));
        assertEquals(904L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void reassignDispatchShouldPublishAssignmentChangedEventFromService() {
        dispatchService.assignRiderToAreaOrder("商务区", 904L, "骑手小王", "老板");
        clearInvocations(realtimeEventPublisher);

        dispatchService.reassignDispatch(
            "ORDER",
            901L,
            "骑手小李",
            "骑手小王",
            "商务区",
            "2026-05-18",
            "LUNCH",
            true,
            "跨区重派",
            "老板"
        );

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.assignment.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小王"));
        assertEquals("商务区", event.payload().get("areaCode"));
        assertEquals("骑手小王", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void getDispatchAiJobLogShouldMarkStaleRouteLabLogAsFailed() {
        long logId = insertDispatchAiJobLog(
            "TEST_LAB",
            "RUNNING",
            "{\"existing\":\"dispatch\"}",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
            null
        );

        var log = dispatchService.getDispatchAiJobLog(logId);

        assertEquals("TEST", log.runType());
        assertEquals("FAILED", log.status());
        assertTrue(log.metadataJson().contains("\"existing\":\"dispatch\""));
        assertTrue(log.metadataJson().contains("\"thinkingStatus\":\"FAILED\""));
        assertTrue(log.metadataJson().contains("\"currentPhase\":\"\u6267\u884c\u4e2d\u65ad\""));
        assertTrue(log.metadataJson().contains("\"thinkingHeadline\":\"\u5386\u53f2\u4efb\u52a1\u672a\u6b63\u786e\u6536\u5c3e\""));
        assertTrue(log.message() != null && !log.message().isBlank());
        assertTrue(log.finishedAt() != null && !log.finishedAt().isBlank());

        var stored = jdbcTemplate.queryForMap(
            "SELECT status, message, finished_at FROM dispatch_ai_job_logs WHERE id = ?",
            logId
        );
        assertEquals("FAILED", stored.get("status"));
        assertEquals(log.message(), stored.get("message"));
        assertNotNull(stored.get("finished_at"));
    }

    @Test
    void deleteJobLogsShouldDeleteAssociatedSuggestionsAndItems() {
        long suggestionId = insertRouteSuggestion();
        insertRouteSuggestionItem(suggestionId, 901L, 1);
        long logId = insertDispatchAiJobLog(
            "TEST_LAB",
            "SUCCESS",
            "{\"existing\":\"dispatch\"}",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
            Timestamp.valueOf(LocalDateTime.now())
        );
        jdbcTemplate.update(
            "UPDATE dispatch_ai_job_logs SET suggestion_id = ?, suggestion_source = ? WHERE id = ?",
            suggestionId,
            "RULE_PLUS_AI",
            logId
        );

        dispatchService.deleteJobLogs(List.of(logId));

        assertEquals(0, countRows("dispatch_ai_job_logs", "id", logId));
        assertEquals(0, countRows("dispatch_route_suggestions", "id", suggestionId));
        assertEquals(0, countRows("dispatch_route_suggestion_items", "suggestion_id", suggestionId));
    }

    @Test
    void getDispatchAiJobLogShouldKeepStaleProductionLogRunning() {
        long logId = insertDispatchAiJobLog(
            "MANUAL_RUN",
            "RUNNING",
            "{\"existing\":\"production\"}",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
            null
        );

        var log = dispatchService.getDispatchAiJobLog(logId);

        assertEquals("PRODUCTION", log.runType());
        assertEquals("RUNNING", log.status());
        assertEquals("{\"existing\":\"production\"}", log.metadataJson());
        assertTrue(log.finishedAt() == null || log.finishedAt().isBlank());

        var stored = jdbcTemplate.queryForMap(
            "SELECT status, finished_at FROM dispatch_ai_job_logs WHERE id = ?",
            logId
        );
        assertEquals("RUNNING", stored.get("status"));
        assertEquals(null, stored.get("finished_at"));
    }

    @Test
    void startRouteLabSimulationShouldReturnRunningResponseAndPersistLog() {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_settings
                SET ai_enabled = TRUE,
                    api_key = 'test-key',
                    balance_available = TRUE
                WHERE id = 1
                """
        );

        var response = dispatchService.startRouteLabSimulation(
            new DispatchRouteLabSimulateRequest(
                List.of("高新区软件园A座", "高新区软件园B座"),
                "NEAR_TO_FAR",
                "高新区软件园北门"
            ),
            "测试员"
        );

        assertTrue(response.logId() > 0);
        assertEquals("RUNNING", response.status());
        assertTrue(response.message() != null && !response.message().isBlank());

        var stored = jdbcTemplate.queryForMap(
            "SELECT trigger_source, meal_period, executed_by FROM dispatch_ai_job_logs WHERE id = ?",
            response.logId()
        );
        assertEquals("TEST", stored.get("trigger_source"));
        assertEquals("LAB", stored.get("meal_period"));
        assertEquals("测试员", stored.get("executed_by"));
    }

    private long insertDispatchAiJobLog(
        String triggerSource,
        String status,
        String metadataJson,
        Timestamp startedAt,
        Timestamp finishedAt
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    INSERT INTO dispatch_ai_job_logs (
                        trigger_source,
                        status,
                        metadata_json,
                        executed_by,
                        started_at,
                        finished_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, triggerSource);
            statement.setString(2, status);
            statement.setString(3, metadataJson);
            statement.setString(4, "test");
            statement.setTimestamp(5, startedAt);
            statement.setTimestamp(6, finishedAt);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long insertRouteSuggestion() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    INSERT INTO dispatch_route_suggestions (
                        serve_date,
                        meal_period,
                        area_code,
                        strategy_mode,
                        anchor_name,
                        anchor_address,
                        algorithm_version,
                        ai_provider,
                        ai_model,
                        suggestion_source,
                        reason_summary,
                        created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setDate(1, java.sql.Date.valueOf(LocalDate.of(2026, 5, 18)));
            statement.setString(2, "LUNCH");
            statement.setString(3, "TEST_AREA");
            statement.setString(4, "NEAR_TO_FAR");
            statement.setString(5, "test-anchor");
            statement.setString(6, "test-address");
            statement.setString(7, "v1");
            statement.setString(8, "deepseek");
            statement.setString(9, "deepseek-chat");
            statement.setString(10, "RULE_PLUS_AI");
            statement.setString(11, "test-summary");
            statement.setString(12, "test");
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertRouteSuggestionItem(long suggestionId, long orderId, int suggestedSequence) {
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_route_suggestion_items (
                    suggestion_id,
                    order_id,
                    suggested_sequence,
                    base_score,
                    adjusted_score,
                    is_ai_adjusted
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            suggestionId,
            orderId,
            suggestedSequence,
            1.0,
            1.0,
            true
        );
    }

    private int countRows(String tableName, String columnName, long value) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
            Integer.class,
            value
        );
        return count == null ? 0 : count;
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
}
