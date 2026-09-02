package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @org.junit.jupiter.api.AfterEach
    void restoreAdminSettings() {
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '11:30' WHERE id = 1");
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_enabled = FALSE WHERE id = 1");
    }
}
