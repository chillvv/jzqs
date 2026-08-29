package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "app.mobile.self-order-cutoff=23:59:59"
})
class MobilePortalRealtimePublishingTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DeliveryService deliveryService;
    private OrderPrepService orderPrepService;
    private TransactionalRealtimePublisher realtimeEventPublisher;
    private RiderOrderStatusRevertModule riderOrderStatusRevertModule;
    private RiderOrderSequenceModule riderOrderSequenceModule;
    private WeChatService weChatService;
    private MobileAuthService mobileAuthService;
    private MobilePortalServiceImpl mobilePortalService;

    @BeforeEach
    void resetFixtures() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate weekStart = tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);

        deliveryService = mock(DeliveryService.class);
        orderPrepService = mock(OrderPrepService.class);
        realtimeEventPublisher = mock(TransactionalRealtimePublisher.class);
        riderOrderStatusRevertModule = mock(RiderOrderStatusRevertModule.class);
        riderOrderSequenceModule = mock(RiderOrderSequenceModule.class);
        weChatService = mock(WeChatService.class);
        mobilePortalService = createService(weChatService);

        when(weChatService.buildDeliveryPage(901L)).thenReturn("pages/orders/index?orderId=901");
        when(weChatService.buildDeliveryPage(902L)).thenReturn("pages/orders/index?orderId=902");
        when(weChatService.buildDeliveryPage(952L)).thenReturn("pages/orders/index?orderId=952");
        when(mobileAuthService.riderProfile(901L)).thenReturn(new com.jzqs.app.mobile.api.RiderAuthProfileResponse(
            901L,
            "骑手小李",
            "李师傅",
            "13800000911",
            "高新区",
            "ACTIVE",
            true,
            "2026-05-18T08:00:00",
            "2026-05-18T08:00:00"
        ));

        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id >= 951");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id >= 951");
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE serve_date BETWEEN ? AND ?", weekStart, weekEnd);
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE week_start_date = ?", weekStart);
        jdbcTemplate.update("DELETE FROM menu_week_items WHERE id >= 951");
        jdbcTemplate.update("DELETE FROM menu_weeks WHERE id >= 951");
        jdbcTemplate.update("DELETE FROM package_plans WHERE id >= 951");
        jdbcTemplate.update("DELETE FROM delivery_receipts WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM customer_delivery_subscriptions WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM delivery_exceptions WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM address_reference_images WHERE customer_address_id >= 901");
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM dispatch_batches WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id >= 901");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 901");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 901");

        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET ordering_enabled = TRUE,
                    delivery_subscribe_enabled = FALSE,
                    delivery_subscribe_lunch_time = '11:30',
                    delivery_subscribe_dinner_time = '17:30',
                    night_order_cutoff_time = '23:00',
                    night_order_open_time = '23:00'
                WHERE id = 1
                """
        );
        insertCustomer(901L, "移动张先生901", "13800000901");
        insertCustomer(902L, "移动李女士902", "13800000902");
        insertCustomer(951L, "用户张先生951", "13800001951");
        insertAddress(901L, 901L, "高新区软件园A座", "高新区");
        insertAddress(902L, 902L, "高新区软件园B座", "高新区");
        insertAddress(951L, 951L, "高新区科技园一期", "高新区");
        insertAddress(952L, 951L, "高新区科技园二期", "高新区");
        insertDailyOrder(901L, 901L);
        insertDailyOrder(902L, 902L);
        insertMealSlotOrder(901L, 901L, 901L, "DISPATCHING");
        insertDinnerOrder(902L, 902L, 902L, "DISPATCHING");
        insertCustomerWalletFixtures();
        insertPublishedMenuFixtures();
        insertCustomerOrderFixtures();

        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, current_openid, auth_status, employment_status,
                    default_area_code, assigned_by, created_at
                ) VALUES (
                    901, '骑手小李', '李师傅', '13800000911', 'rider_openid_901', 'ACTIVE', 'ACTIVE',
                    '高新区', '老板', CURRENT_TIMESTAMP
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_assignments (
                    id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number
                ) VALUES
                    (901, 901, '骑手小李', 901, '高新区', 'DISPATCHING', 1),
                    (902, 902, '骑手小李', 901, '高新区', 'DISPATCHING', 2)
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code,
                    batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    901, ?, 'LUNCH', 901, '高新区', 'IN_PROGRESS', 2, 0, 1
                )
                """,
            LocalDate.now()
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batch_items (
                    id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                ) VALUES
                    (901, 901, 901, 1, 1, 'CURRENT', FALSE),
                    (902, 901, 902, 2, 2, 'PENDING', FALSE)
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (
                    id, meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, visible_to_customer
                ) VALUES (
                    901, 901, '/uploads/rider-receipts/2026-05-18/original.jpg', '初始回执',
                    TIMESTAMP '2026-05-18 12:00:00', TIMESTAMP '2026-05-18 12:00:00', TRUE
                )
                """
        );
        when(deliveryService.recordDeliveryReceipt(901L, "/uploads/test.jpg", "放前台", "2026-05-18T12:10", "2026-05-18T12:10", "2026-05-20T12:10"))
            .thenReturn(new DeliveryReceiptRecordResponse(901L, "DELIVERED", "SKIPPED", "SKIPPED", "/uploads/test.jpg", "2026-05-18T12:10", "2026-05-20T12:10"));
        when(orderPrepService.cancelOrder(952L))
            .thenReturn(new OrderActionResponse(952L, "CANCELLED"));
    }

    private MobilePortalServiceImpl createService(WeChatService weChatService) {
        return createService(weChatService, mock(OrderNoteSnapshotService.class));
    }

    private MobilePortalServiceImpl createService(WeChatService weChatService, OrderNoteSnapshotService orderNoteSnapshotService) {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeAudienceModule realtimeAudienceModule = new RealtimeAudienceModule(realtimeEventPublisher);
        com.jzqs.app.settings.service.SettingsService settingsService = mock(com.jzqs.app.settings.service.SettingsService.class);
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "00:00", false, "", "", "", false, "", "", "", ""
        ));
        DeliverySubscriptionModule deliverySubscriptionModule = new DeliverySubscriptionModule(jdbcTemplate, weChatService, objectMapper, settingsService);
        MobileCustomerQueryModule mobileCustomerQueryModule = new MobileCustomerQueryModule(
            jdbcTemplate,
            objectMapper,
            deliverySubscriptionModule
        );
        DispatchService dispatchService = mock(DispatchService.class);
        RiderQueueSupport riderQueueSupport = new RiderQueueSupport(jdbcTemplate, objectMapper, realtimeAudienceModule);
        RiderReceiptStorageSupport riderReceiptStorageSupport = new RiderReceiptStorageSupport("./uploads");
        RiderDeliveryEvidenceModule riderDeliveryEvidenceModule = new RiderDeliveryEvidenceModule(
            jdbcTemplate,
            deliveryService,
            riderQueueSupport,
            riderReceiptStorageSupport,
            deliverySubscriptionModule,
            realtimeAudienceModule
        );
        MiniappOrderModule miniappOrderModule = new MiniappOrderModule(
            jdbcTemplate,
            dispatchService,
            orderNoteSnapshotService,
            realtimeAudienceModule,
            riderQueueSupport
        );
        MobileAddressModule mobileAddressModule = new MobileAddressModule(jdbcTemplate);
        NightlyReminderModule nightlyReminderModule = mock(NightlyReminderModule.class);
        mobileAuthService = mock(MobileAuthService.class);
        return new MobilePortalServiceImpl(
            jdbcTemplate,
            orderPrepService,
            riderQueueSupport,
            riderReceiptStorageSupport,
            objectMapper,
            weChatService,
            mobileCustomerQueryModule,
            riderDeliveryEvidenceModule,
            riderOrderStatusRevertModule,
            riderOrderSequenceModule,
            mock(AftersaleService.class),
            realtimeAudienceModule,
            deliverySubscriptionModule,
            nightlyReminderModule,
            miniappOrderModule,
            mobileAddressModule,
            mobileAuthService,
            "23:59:59"
        );
    }

    @Test
    void createMiniappOrderShouldNotFailWhenSnapshotWriteFails() {
        // 核心意图：快照写入失败时下单仍成功，订单被创建/合并且状态正常
        OrderNoteSnapshotService brokenSnapshotService = mock(OrderNoteSnapshotService.class);
        doThrow(new IllegalStateException("snapshot exploded"))
            .when(brokenSnapshotService)
            .writeOrderSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        MobilePortalServiceImpl service = createService(weChatService, brokenSnapshotService);

        MobileCreateOrderResponse result = assertDoesNotThrow(() -> service.createMiniappOrder(
            951L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区科技园一期",
            "",
            1
        ));

        assertNotNull(result.orderId());
        Integer qty = jdbcTemplate.queryForObject(
            "SELECT quantity FROM meal_slot_orders WHERE id = ?", Integer.class, result.orderId());
        assertNotNull(qty);
        assertTrue(qty >= 1);
    }

    @Test
    void submitRiderReceiptShouldPublishReceiptChangedEventFromService() {
        mobilePortalService.submitRiderReceipt(901L, 901L, "/uploads/test.jpg", "放前台", "2026-05-18T12:10:00");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, atLeastOnce()).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();
        RealtimeEvent event = events.stream()
            .filter(e -> "dispatch.receipt.changed".equals(e.eventType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少 dispatch.receipt.changed 事件，实际: " + events.stream().map(RealtimeEvent::eventType).toList()));

        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void submitRiderReceiptShouldNotSendSubscribeMessageImmediately() {
        // 远程行为：送达后不再即时发送订阅消息，统一由定时任务在餐期释放时间点发送，
        // 或由后台手动"立即释放"后发送。此处验证送达回执不会触发即时发送。
        seedDeliverySubscription(901L, 901L, "tmpl-lunch");
        String deliveredAt = todayAt("11:20");
        when(deliveryService.recordDeliveryReceipt(901L, "/uploads/test.jpg", "放前台", deliveredAt, deliveredAt, LocalDateTime.parse(deliveredAt).plusHours(48).toString()))
            .thenReturn(new DeliveryReceiptRecordResponse(
                901L,
                "DELIVERED",
                "SKIPPED",
                "SKIPPED",
                "/uploads/test.jpg",
                deliveredAt,
                LocalDateTime.parse(deliveredAt).plusHours(48).toString()
            ));

        mobilePortalService.submitRiderReceipt(901L, 901L, "/uploads/test.jpg", "放前台", deliveredAt);

        verify(weChatService, never()).sendDeliverySubscribeMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals("AUTHORIZED", jdbcTemplate.queryForObject(
            "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 901",
            String.class
        ));
    }

    @Test
    void submitRiderReceiptAfterLunchCutoffShouldStillNotSendImmediately() {
        // 即使送达时间在餐期释放时间之后，送达回执本身也不触发订阅发送（由定时/手动释放触发）
        seedDeliverySubscription(901L, 901L, "tmpl-lunch");
        String deliveredAt = todayAt("11:40");
        when(deliveryService.recordDeliveryReceipt(901L, "/uploads/test.jpg", "放前台", deliveredAt, deliveredAt, LocalDateTime.parse(deliveredAt).plusHours(48).toString()))
            .thenReturn(new DeliveryReceiptRecordResponse(
                901L,
                "DELIVERED",
                "SKIPPED",
                "SKIPPED",
                "/uploads/test.jpg",
                deliveredAt,
                LocalDateTime.parse(deliveredAt).plusHours(48).toString()
            ));

        mobilePortalService.submitRiderReceipt(901L, 901L, "/uploads/test.jpg", "放前台", deliveredAt);

        verify(weChatService, never()).sendDeliverySubscribeMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals("AUTHORIZED", jdbcTemplate.queryForObject(
            "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 901",
            String.class
        ));
    }

    @Test
    void shouldSendScheduledDeliverySubscribeMessagesForLunchAndDinnerIndependently() {
        // 订阅发送开关需打开：远程实现中 delivery_subscribe_enabled=false 时统一不发送
        // 释放时间设为过去时刻，避免测试在凌晨运行（当前时间<11:30）扫不到订单
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_enabled = TRUE, delivery_subscribe_lunch_time = '00:00', delivery_subscribe_dinner_time = '00:00' WHERE id = 1");
        seedDeliverySubscription(901L, 901L, "tmpl-lunch");
        seedDeliverySubscription(902L, 902L, "tmpl-dinner");
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id IN (901, 902)");
        // 定时发送只扫描 visible_to_customer=FALSE 的回执：901 需改回 FALSE，902 需补一条回执
        jdbcTemplate.update("UPDATE delivery_receipts SET visible_to_customer = FALSE WHERE meal_slot_order_id = 901");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, visible_to_customer)
                VALUES (902, 902, '/uploads/rider-receipts/2026-05-18/dinner.jpg', '晚餐回执',
                        TIMESTAMP '2026-05-18 12:00:00', TIMESTAMP '2026-05-18 12:00:00', FALSE)
                """);

        assertEquals(1, mobilePortalService.sendScheduledDeliverySubscribeMessages("LUNCH"));
        assertEquals(1, mobilePortalService.sendScheduledDeliverySubscribeMessages("DINNER"));

        verify(weChatService, times(2)).sendDeliverySubscribeMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(
            List.of("SENT", "SENT"),
            jdbcTemplate.queryForList(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id IN (901, 902) ORDER BY meal_slot_order_id",
                String.class
            )
        );
    }

    @Test
    void scheduledSendShouldSkipAlreadySentSubscriptions() {
        seedDeliverySubscription(901L, 901L, "tmpl-lunch");
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = 901");
        jdbcTemplate.update("UPDATE customer_delivery_subscriptions SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE meal_slot_order_id = 901");

        assertEquals(0, mobilePortalService.sendScheduledDeliverySubscribeMessages("LUNCH"));

        verify(weChatService, never()).sendDeliverySubscribeMessage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void updateRiderReceiptShouldPublishReceiptChangedEventFromService() {
        mobilePortalService.updateRiderReceipt(901L, 901L, "/uploads/test-2.jpg", "已更新", "2026-05-18T12:15:00");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(2)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();
        assertEquals(List.of("customer.order.changed", "dispatch.receipt.changed"), events.stream().map(RealtimeEvent::eventType).toList());
        RealtimeEvent event = events.get(1);

        assertEquals("dispatch.receipt.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void deleteRiderReceiptImageShouldPublishReceiptChangedEventFromService() {
        mobilePortalService.deleteRiderReceiptImage(901L, 901L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(2)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();
        assertEquals(List.of("customer.order.changed", "dispatch.receipt.changed"), events.stream().map(RealtimeEvent::eventType).toList());
        RealtimeEvent event = events.get(1);

        assertEquals("dispatch.receipt.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void revertOrderStatusShouldPublishReceiptChangedEventFromService() {
        when(riderOrderStatusRevertModule.revertOrderStatus(901L, 901L))
            .thenReturn(new RiderOrderStatusRevertResponse(901L, "PENDING"));

        mobilePortalService.revertOrderStatus(901L, 901L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(2)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();
        assertEquals(List.of("customer.order.changed", "dispatch.receipt.changed"), events.stream().map(RealtimeEvent::eventType).toList());
        RealtimeEvent event = events.get(1);

        assertEquals("dispatch.receipt.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void reorderRiderQueueShouldPublishQueueChangedEventFromService() {
        mobilePortalService.reorderRiderQueue(901L, List.of(902L, 901L));

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(902L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void deferRiderQueueItemShouldPublishQueueChangedEventFromService() {
        mobilePortalService.deferRiderQueueItem(901L, 901L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void resumeRiderQueueItemShouldPublishQueueChangedEventFromService() {
        jdbcTemplate.update("UPDATE dispatch_batch_items SET item_status = 'DEFERRED' WHERE id = 901");

        mobilePortalService.resumeRiderQueueItem(901L, 901L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.queue.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void reportDeliveryExceptionShouldPublishExceptionChangedEventFromService() {
        mobilePortalService.reportDeliveryException(901L, 901L, "PHONE_OFF", "联系不上", List.of("/uploads/e1.jpg"));

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.exception.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void createMiniappOrderShouldPublishOrderAndWalletChangedEventsFromService() {
        // 重置 seed 的 952 订单 quantity=1，确保合并行为确定（测试间隔离）
        jdbcTemplate.update("UPDATE meal_slot_orders SET quantity = 1 WHERE id = 952");

        MobileCreateOrderResponse result = mobilePortalService.createMiniappOrder(
            951L,
            LocalDate.now().plusDays(1).toString(),
            "LUNCH",
            "高新区科技园一期",
            "少饭",
            1
        );

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(3)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();

        List<String> types = events.stream().map(RealtimeEvent::eventType).toList();
        // 远程实现：dispatch.queue.changed + customer.order.changed + customer.wallet.changed
        assertEquals(List.of("dispatch.queue.changed", "customer.order.changed", "customer.wallet.changed"), types);
        RealtimeEvent orderEvent = events.stream().filter(e -> "customer.order.changed".equals(e.eventType())).findFirst().orElseThrow();
        RealtimeEvent walletEvent = events.stream().filter(e -> "customer.wallet.changed".equals(e.eventType())).findFirst().orElseThrow();
        assertEquals(951L, ((Number) orderEvent.payload().get("customerId")).longValue());
        assertEquals(result.orderId(), ((Number) orderEvent.payload().get("orderId")).longValue());
        assertEquals(result.orderId(), ((Number) walletEvent.payload().get("orderId")).longValue());
    }

    @Test
    void cancelMiniappOrderShouldPublishOrderAndWalletChangedEventsFromService() {
        mobilePortalService.cancelMiniappOrder(951L, 952L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(3)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();

        List<String> types = events.stream().map(RealtimeEvent::eventType).toList();
        // 实际顺序：customer.order.changed + customer.wallet.changed + dispatch.queue.changed
        assertEquals(List.of("customer.order.changed", "customer.wallet.changed", "dispatch.queue.changed"), types);
        RealtimeEvent orderEvent = events.stream().filter(e -> "customer.order.changed".equals(e.eventType())).findFirst().orElseThrow();
        assertEquals(951L, ((Number) orderEvent.payload().get("customerId")).longValue());
        assertEquals(952L, ((Number) orderEvent.payload().get("orderId")).longValue());
    }

    @Test
    void changeCustomerOrderAddressShouldPublishOrderChangedEventFromService() {
        mobilePortalService.changeCustomerOrderAddress(951L, 953L, 952L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("customer.order.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("customer:id:951"));
        assertEquals(951L, ((Number) event.payload().get("customerId")).longValue());
        assertEquals(953L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void deleteMiniappOrderShouldPublishOrderAndWalletChangedEventsFromService() {
        mobilePortalService.deleteMiniappOrder(951L, 954L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        InOrder inOrder = inOrder(realtimeEventPublisher);
        inOrder.verify(realtimeEventPublisher, times(2)).publish(eventCaptor.capture());
        List<RealtimeEvent> events = eventCaptor.getAllValues();

        assertEquals(List.of("customer.order.changed", "customer.wallet.changed"), events.stream().map(RealtimeEvent::eventType).toList());
        assertEquals(951L, ((Number) events.get(0).payload().get("customerId")).longValue());
        assertEquals(954L, ((Number) events.get(0).payload().get("orderId")).longValue());
    }

    private void insertCustomer(long id, String name, String phone) {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source) VALUES (?, ?, ?, 'MINIAPP')",
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

    private void insertDailyOrder(long id, long customerId) {
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'MINIAPP', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)
                """,
            id,
            customerId,
            LocalDate.now()
        );
    }

    private void insertMealSlotOrder(long id, long dailyOrderId, long addressId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', ?, 'MINIAPP')
                """,
            id,
            dailyOrderId,
            addressId,
            status
        );
    }

    private void insertDinnerOrder(long id, long dailyOrderId, long addressId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'DINNER', 'DINNER', 1, ?, '-', '-', ?, 'MINIAPP')
                """,
            id,
            dailyOrderId,
            addressId,
            status
        );
    }

    private void seedDeliverySubscription(long customerId, long mealSlotOrderId, String templateId) {
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            customerId,
            mealSlotOrderId,
            templateId
        );
        jdbcTemplate.update(
            "UPDATE customers SET current_openid = ? WHERE id = ?",
            "openid_" + customerId,
            customerId
        );
    }

    private void insertCustomerWalletFixtures() {
        jdbcTemplate.update(
            "INSERT INTO package_plans (id, package_code, package_name, total_meals, enabled) VALUES (951, 'MOBILE_RT', '移动实时套餐', 30, TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (951, 951, 951, 30, 0, 0, TRUE)"
        );
    }

    private void insertPublishedMenuFixtures() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate weekStart = tomorrow.minusDays(tomorrow.getDayOfWeek().getValue() - 1L);
        LocalDate weekEnd = weekStart.plusDays(6);
        jdbcTemplate.update(
            "INSERT INTO menu_weeks (id, week_start_date, week_end_date, status, published_at, created_by, published_by) VALUES (951, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP, 'test', 'test')",
            weekStart,
            weekEnd
        );
        jdbcTemplate.update(
            """
                INSERT INTO menu_week_items (
                    id, week_id, serve_date, weekday_index, meal_period, slot_status, dish_items_json,
                    total_calories, meal_name, meal_detail, calories, merchant_note, image_url, sort_order
                ) VALUES (
                    951, 951, ?, ?, 'LUNCH', 'ACTIVE', '["黑椒牛柳","米饭"]',
                    520, '黑椒牛柳饭', '黑椒牛柳+米饭', 520, '少油', '/assets/meal-default.jpeg', 1
                )
                """,
            tomorrow,
            tomorrow.getDayOfWeek().getValue()
        );
    }

    private void insertCustomerOrderFixtures() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate afterTomorrow = LocalDate.now().plusDays(2);
        LocalDate hiddenOrderDate = LocalDate.now().plusDays(3);
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (952, 951, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            tomorrow
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type) VALUES (952, 952, 'LUNCH', 'LUNCH', 1, 951, '-', '-', 'PENDING_DISPATCH', 'MINIAPP')"
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (953, 951, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            afterTomorrow
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type) VALUES (953, 953, 'LUNCH', 'LUNCH', 1, 951, '-', '-', 'PENDING_DISPATCH', 'MINIAPP')"
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (954, 951, ?, 'MINIAPP', 'CANCELLED', FALSE, CURRENT_TIMESTAMP)",
            hiddenOrderDate
        );
        jdbcTemplate.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type, visible_to_customer) VALUES (954, 954, 'LUNCH', 'LUNCH', 1, 951, '-', '-', 'CANCELLED', 'MINIAPP', TRUE)"
        );
    }

    private String todayAt(String hourMinute) {
        return LocalDate.now() + "T" + hourMinute;
    }
}
