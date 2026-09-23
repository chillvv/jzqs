package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.settings.service.SettingsService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DeliverySubscriptionModuleTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WeChatService weChatService;
    private SettingsService settingsService;
    private DeliverySubscriptionModule module;

    @BeforeEach
    void setUp() {
        weChatService = mock(WeChatService.class);
        settingsService = mock(SettingsService.class);
        // 订阅发送开关默认关闭（与 setUp 里 delivery_subscribe_enabled=FALSE 一致），
        // 避免裸 mock 返回 null 导致 NPE
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, false, "11:30", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        module = new DeliverySubscriptionModule(
            jdbcTemplate,
            weChatService,
            new ObjectMapper(),
            settingsService
        );

        jdbcTemplate.update("DELETE FROM customer_delivery_subscriptions WHERE meal_slot_order_id >= 981");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 981");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 981");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 981");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 981");
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_enabled = FALSE WHERE id = 1");

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (981, '订阅模块客户', '13800000981', 'MINIAPP', TRUE)"
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (981, 981, '订阅模块客户', '13800000981', '高新区订阅路1号', '高新区', TRUE)
                """
        );
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (981, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now()
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (981, 981, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """
        );
    }

    @Test
    void authorizeSubscriptionShouldResetExistingFailureState() {
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at, sent_at, last_error_message
                ) VALUES (?, ?, ?, 'FAILED', 'OLD_SOURCE', ?, ?, ?)
                """,
            981L,
            981L,
            "tmpl-old",
            Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
            Timestamp.valueOf(LocalDateTime.now().minusHours(1)),
            "boom"
        );

        module.authorizeSubscription(981L, 981L, "tmpl-new");

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
                SELECT template_id, status, source, sent_at, last_error_message
                FROM customer_delivery_subscriptions
                WHERE meal_slot_order_id = 981
                """
        );
        assertEquals("tmpl-new", row.get("template_id"));
        assertEquals("AUTHORIZED", row.get("status"));
        assertEquals("MINIAPP_ORDER_SUCCESS", row.get("source"));
        assertNull(row.get("sent_at"));
        assertNull(row.get("last_error_message"));
    }

    @Test
    void releaseAndSendShouldSkipWhenFixedTimeModeEnabled() {
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_enabled = TRUE WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            981L,
            "tmpl-fixed"
        );

        var result = module.releaseAndSendWithReason(981L);

        org.junit.jupiter.api.Assertions.assertFalse(result.sent());
        verify(weChatService, never()).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void scheduledScanShouldSendReminderForOrderDeliveredAfterReleaseTime() {
        // 场景：订单在餐期释放时间之后才送达（回执创建时即对用户可见），订阅仍为 AUTHORIZED。
        // 定时扫描应补发送餐提醒（修复前仅扫 visible_to_customer = FALSE，该场景被永久跳过）。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        // 释放时间设为 00:00，保证任意时刻运行测试都已过释放时间
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, TRUE)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            981L,
            "tmpl-after-release"
        );

        int sentCount = module.sendScheduledMessages("LUNCH");

        assertEquals(1, sentCount);
        verify(weChatService).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        assertEquals(
            "SENT",
            jdbcTemplate.queryForObject(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 981",
                String.class
            )
        );

        // 已发送后再次扫描不重复发送
        assertEquals(0, module.sendScheduledMessages("LUNCH"));
    }

    @Test
    void dinnerOrderShouldSendWithItsOwnTemplate() {
        // 晚餐订单必须用它自己绑定的模板下发：两个取餐模板的字段编号完全不同
        // （「取餐提醒」thing6/phone_number9/thing10/thing7，
        //  「订餐提醒」thing7/phone_number5/thing4/thing2），用错模板微信会直接报参数错误；
        //  且两模板额度独立，不可互相顶替。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_dinner_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (982, 981, 'DINNER', 'DINNER', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1982, 982, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            982L,
            "tmpl-dinner"
        );

        // 裸 mock 的 buildDeliveryPage 默认返回 null，先给出真实值，page 参数才可控
        given(weChatService.buildDeliveryPage(org.mockito.ArgumentMatchers.anyLong()))
            .willReturn("pages/orders/index?orderId=982");

        assertEquals(1, module.sendScheduledMessages("DINNER"));

        org.mockito.ArgumentCaptor<String> templateCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(weChatService).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.anyString(),
            templateCaptor.capture(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            // 骑手电话：这批测试数据没有骑手，实际传参是 null —— Mockito 的 anyString() 不匹配 null，
            // 必须用 nullable，否则这里会误报「参数不同」而掩盖真正的模板断言。
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        assertEquals("tmpl-dinner", templateCaptor.getValue());
    }

    @Test
    void scheduledScanShouldStopRetryingAfterMaxRetries() {
        // 场景：某条订阅消息已失败达到最大重试次数。
        // 定时扫描不应再反复调用微信接口（此前会每分钟无限重试，把后台拖慢）。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, TRUE)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at, retry_count
                ) VALUES (?, ?, ?, 'FAILED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP, ?)
                """,
            981L,
            981L,
            "tmpl-exhausted",
            DeliverySubscriptionModule.MAX_SEND_RETRIES
        );

        assertEquals(0, module.sendScheduledMessages("LUNCH"));

        verify(weChatService, never()).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void revokedSubscriptionShouldBecomeCancelledAndNeverRetried() {
        // 场景：用户已在微信端拒收（43101）。应直接置为终态 CANCELLED，
        // 后续扫描不再重复调用微信接口（此前会每分钟空转，产生海量错误日志）。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, TRUE)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            981L,
            "tmpl-revoked"
        );

        doThrow(new com.jzqs.app.common.error.BusinessException(
            com.jzqs.app.common.error.ErrorCode.SUBSCRIPTION_REVOKED_BY_USER,
            "用户关闭了订阅消息权限"
        )).when(weChatService).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );

        assertEquals(0, module.sendScheduledMessages("LUNCH"));
        assertEquals(
            "CANCELLED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 981",
                String.class
            )
        );

        // 再次扫描不应再尝试发送
        assertEquals(0, module.sendScheduledMessages("LUNCH"));
        verify(weChatService, times(1)).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldInheritConsentFromSameTemplateWhenOrderHasNoSubscriptionRecord() {
        // 前端落库请求丢失时订单会完全没有订阅记录（历史故障：一次下单两个餐段，其中一个的
        // 落库请求丢失，该单永久收不到通知）。若客户在同一模板上仍有未消耗授权，后端应补写记录并下发。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        given(weChatService.resolveDeliveryTemplateId(org.mockito.ArgumentMatchers.any())).willReturn("tmpl-lunch");
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        // 同一客户在另一天的订单上保有同模板未消耗授权（订单 981 自身的订阅记录丢失）
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (983, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now().minusDays(1)
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (983, 983, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            983L,
            "tmpl-lunch"
        );

        assertEquals(1, module.sendScheduledMessages("LUNCH"));

        assertEquals(
            "SENT",
            jdbcTemplate.queryForObject(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 981",
                String.class
            )
        );
    }

    @Test
    void alreadySentSubscriptionShouldNotBeResurrectedByLateBinding() {
        // 场景：下单时授权落库请求丢失，顾客第二天才打开小程序、延迟补写。
        // 此时该订单的通知可能已经发出：补写不能把 SENT 重置回 AUTHORIZED，
        // 否则定时任务会给同一订单再推一次重复的取餐提醒。
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at, sent_at
                ) VALUES (?, ?, ?, 'SENT', 'MINIAPP_ORDER_SUCCESS', ?, ?)
                """,
            981L,
            981L,
            "tmpl-already-sent",
            Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
            Timestamp.valueOf(LocalDateTime.now().minusHours(1))
        );

        module.authorizeSubscription(981L, 981L, "tmpl-new-after-send");

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
                SELECT template_id, status, sent_at
                FROM customer_delivery_subscriptions
                WHERE meal_slot_order_id = 981
                """
        );
        assertEquals("SENT", row.get("status"));
        // 已发送的状态下，模板被后续补写覆盖也不会改写记录（额度已消耗，再发必然失败）
        assertEquals("tmpl-already-sent", row.get("template_id"));
        org.junit.jupiter.api.Assertions.assertNotNull(row.get("sent_at"));
    }

    @Test
    void inheritedFallbackShouldRunAfterOrdersWithOwnAuthorization() {
        // 场景：同一客户同一模板只有一条可用额度，但今天有两单——一单有自己的授权记录，
        // 另一单落库丢失需要"借用"授权。必须先把额度给真正点了「允许」的那一单，
        // 否则正常下单的顾客会收不到通知（借用是猜的，自己的授权是确定的）。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        given(weChatService.resolveDeliveryTemplateId(org.mockito.ArgumentMatchers.any())).willReturn("tmpl-lunch");
        given(weChatService.buildDeliveryPage(org.mockito.ArgumentMatchers.anyLong()))
            .willAnswer(invocation -> "pages/orders/index?orderId=" + invocation.getArgument(0));

        // 982：有自己的授权记录（正常下单）
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (982, 981, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1982, 982, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            982L,
            "tmpl-lunch"
        );
        // 981：无订阅记录，只能走继承兜底（排在自己有授权的单之后）
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        // 供借用的一条真实授权（挂在昨天的订单上）
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (983, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now().minusDays(1)
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (983, 983, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            983L,
            "tmpl-lunch"
        );

        assertEquals(2, module.sendScheduledMessages("LUNCH"));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(weChatService);
        inOrder.verify(weChatService).buildDeliveryPage(982L);
        inOrder.verify(weChatService).buildDeliveryPage(981L);
    }

    @Test
    void inheritedRowShouldNotServeAsConsentEvidence() {
        // 场景：兜底补写的继承记录不能被当成"客户已授权"的凭据再次出借，
        // 否则一条历史授权会被无限复制成每天都"有额度"的假象，每天都在发必然失败的请求。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        given(weChatService.resolveDeliveryTemplateId(org.mockito.ArgumentMatchers.any())).willReturn("tmpl-lunch");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (983, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now().minusDays(1)
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (983, 983, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'INHERITED_FROM_CUSTOMER_CONSENT', CURRENT_TIMESTAMP)
                """,
            981L,
            983L,
            "tmpl-lunch"
        );

        assertEquals(0, module.sendScheduledMessages("LUNCH"));
        // 没有任何真实授权凭据，就不应补写出一条注定失败的继承记录
        assertEquals(
            0,
            jdbcTemplate.queryForList(
                "SELECT id FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 981"
            ).size()
        );
        verify(weChatService, never()).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldNotBorrowConsentFromUpcomingOrder() {
        // 场景：顾客为明天的餐点了「允许」，那条授权是明天那单自己的额度。
        // 今天有条没记录的单不能借用它——借用会把明天那单的额度花掉，让正常下单的顾客明天收不到通知。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        given(weChatService.resolveDeliveryTemplateId(org.mockito.ArgumentMatchers.any())).willReturn("tmpl-lunch");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        // 明天的订单：持有真实授权，且还没到自己的餐期
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (984, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now().plusDays(1)
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (984, 984, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'PENDING_DISPATCH', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            984L,
            "tmpl-lunch"
        );

        assertEquals(0, module.sendScheduledMessages("LUNCH"));
        verify(weChatService, never()).sendDeliverySubscribeMessage(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        // 明天那单的授权必须原封不动
        assertEquals(
            "AUTHORIZED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 984",
                String.class
            )
        );
    }

    @Test
    void borrowedConsentShouldBeMarkedConsumedAfterSuccessfulSend() {
        // 场景：一次「允许」只够发一条。借用出去后必须把原授权记录标记为已消耗，
        // 否则同一条额度会被反复借用，导致本该收得到通知的订单变成 43101。
        given(settingsService.operationSettings()).willReturn(new com.jzqs.app.settings.api.OperationSettingsResponse(
            true, "接单中", "", "", "", "[]", 3, 7, 3, false, true, "00:00", "17:30", false, "", "", "", false, "", "", "", ""
        ));
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '00:00' WHERE id = 1");
        jdbcTemplate.update("UPDATE customers SET current_openid = 'openid_981' WHERE id = 981");
        given(weChatService.resolveDeliveryTemplateId(org.mockito.ArgumentMatchers.any())).willReturn("tmpl-lunch");
        jdbcTemplate.update(
            """
                INSERT INTO delivery_receipts (id, meal_slot_order_id, receipt_url, delivered_at, visible_to_customer)
                VALUES (1981, 981, '/uploads/r.jpg', CURRENT_TIMESTAMP, FALSE)
                """);
        jdbcTemplate.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at) VALUES (983, 981, ?, 'MINIAPP', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)",
            LocalDate.now().minusDays(1)
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (983, 983, 'LUNCH', 'LUNCH', 1, 981, '-', '-', 'DELIVERED', 'MINIAPP')
                """);
        jdbcTemplate.update(
            """
                INSERT INTO customer_delivery_subscriptions (
                    customer_id, meal_slot_order_id, template_id, status, source, authorized_at
                ) VALUES (?, ?, ?, 'AUTHORIZED', 'MINIAPP_ORDER_SUCCESS', CURRENT_TIMESTAMP)
                """,
            981L,
            983L,
            "tmpl-lunch"
        );

        assertEquals(1, module.sendScheduledMessages("LUNCH"));
        assertEquals(
            "CANCELLED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM customer_delivery_subscriptions WHERE meal_slot_order_id = 983",
                String.class
            )
        );
    }

    @org.junit.jupiter.api.AfterEach
    void restoreAdminSettings() {
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '11:30' WHERE id = 1");
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_dinner_time = '17:30' WHERE id = 1");
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_enabled = FALSE WHERE id = 1");
    }
}
