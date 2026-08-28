package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class RiderDeliveryEvidenceModuleTest {
    private static final long CUSTOMER_ID = 9861L;
    private static final long ADDRESS_ID = 9861L;
    private static final long ADDRESS_ID_2 = 9862L;
    private static final long DAILY_ORDER_ID = 9861L;
    private static final long ORDER_ID = 9861L;
    private static final long RIDER_PROFILE_ID = 9861L;
    private static final long BATCH_ID = 9861L;
    private static final long BATCH_ITEM_ID = 9861L;
    private static final String RIDER_NAME = "证据模块骑手";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DeliveryService deliveryService;
    private RiderDeliveryEvidenceModule riderDeliveryEvidenceModule;

    @BeforeEach
    void resetSeedData() {
        deliveryService = mock(DeliveryService.class);
        RealtimeAudienceModule realtimeAudienceModule = new RealtimeAudienceModule(mock(TransactionalRealtimePublisher.class));
        RiderQueueSupport riderQueueSupport = new RiderQueueSupport(jdbcTemplate, new ObjectMapper(), realtimeAudienceModule);
        DeliverySubscriptionModule deliverySubscriptionModule = new DeliverySubscriptionModule(
            jdbcTemplate,
            mock(WeChatService.class),
            new ObjectMapper(),
            mock(com.jzqs.app.settings.service.SettingsService.class)
        );
        riderDeliveryEvidenceModule = new RiderDeliveryEvidenceModule(
            jdbcTemplate,
            deliveryService,
            riderQueueSupport,
            new RiderReceiptStorageSupport("./uploads"),
            deliverySubscriptionModule,
            realtimeAudienceModule
        );

        jdbcTemplate.update("DELETE FROM address_reference_images WHERE customer_address_id IN (?, ?)", ADDRESS_ID, ADDRESS_ID_2);
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE id = ?", BATCH_ITEM_ID);
        jdbcTemplate.update("DELETE FROM dispatch_batches WHERE id = ?", BATCH_ID);
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id IN (?, ?)", ADDRESS_ID, ADDRESS_ID_2);
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id = ?", RIDER_PROFILE_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '证据模块客户', '13900009861', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES
                    (?, ?, '证据模块客户', '13900009861', '高新区测试路 1 号', '高新区', TRUE),
                    (?, ?, '证据模块客户', '13900009861', '高新区测试路 2 号', '高新区', FALSE)
                """,
            ADDRESS_ID,
            CUSTOMER_ID,
            ADDRESS_ID_2,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'MINIAPP', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)
                """,
            DAILY_ORDER_ID,
            CUSTOMER_ID,
            LocalDate.now()
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', 'DISPATCHING', 'MINIAPP')
                """,
            ORDER_ID,
            DAILY_ORDER_ID,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status, default_area_code, display_order, created_at
                ) VALUES (
                    ?, ?, ?, '13800009861', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP
                )
                """,
            RIDER_PROFILE_ID,
            RIDER_NAME,
            RIDER_NAME
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_assignments (
                    id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number
                ) VALUES (?, ?, ?, ?, '高新区', 'DISPATCHING', 1)
                """,
            ORDER_ID,
            ORDER_ID,
            RIDER_NAME,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    ?, CURRENT_DATE, 'LUNCH', ?, '高新区', 'IN_PROGRESS', 1, 0, 1
                )
                """,
            BATCH_ID,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batch_items (
                    id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                ) VALUES (?, ?, ?, 1, 1, 'CURRENT', FALSE)
                """,
            BATCH_ITEM_ID,
            BATCH_ID,
            ORDER_ID
        );
    }

    @Test
    @Transactional
    void shouldSaveBatchAddressReferenceImageWithDedupedIds() {
        RiderAddressReferenceBatchSaveResponse result = riderDeliveryEvidenceModule.saveBatchAddressReferenceImage(
            RIDER_PROFILE_ID,
            new RiderBatchAddressReferenceRequest(
                java.util.List.of(ADDRESS_ID, ADDRESS_ID_2, ADDRESS_ID),
                "reference-batch.jpg"
            )
        );

        assertEquals(2, result.updatedCount());
        assertIterableEquals(java.util.List.of(ADDRESS_ID, ADDRESS_ID_2), result.addressIds());
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM address_reference_images WHERE customer_address_id IN (?, ?)",
            Integer.class,
            ADDRESS_ID,
            ADDRESS_ID_2
        ));
        assertEquals("reference-batch.jpg", jdbcTemplate.queryForObject(
            "SELECT reference_image_url FROM address_reference_images WHERE customer_address_id = ?",
            String.class,
            ADDRESS_ID
        ));
    }

    @Test
    @Transactional
    void shouldSubmitReceiptAndAutoSaveAddressReferenceImage() {
        // 明确设置餐期释放时间：LUNCH=11:30，保证 15:20 送达时 visibleAt=deliveredAt
        jdbcTemplate.update("UPDATE admin_settings SET delivery_subscribe_lunch_time = '11:30' WHERE id = 1");
        // 送达时间取当天 15:20（晚于餐期释放阈值），保证 visibleAt 恒等于 deliveredAt，测试自洽
        String deliveredAt = LocalDate.now().atTime(15, 20).toString();
        String visibleAt = deliveredAt;
        String expiresAt = LocalDate.now().atTime(15, 20).plusHours(48).toString();
        when(deliveryService.recordDeliveryReceipt(
            ORDER_ID,
            "receipt-module.jpg",
            "已送达",
            deliveredAt,
            visibleAt,
            expiresAt
        )).thenReturn(new DeliveryReceiptRecordResponse(
            ORDER_ID,
            "DELIVERED",
            "SKIPPED",
            "SKIPPED",
            "receipt-module.jpg",
            visibleAt,
            expiresAt
        ));

        riderDeliveryEvidenceModule.submitRiderReceipt(ORDER_ID, RIDER_PROFILE_ID, "receipt-module.jpg", "已送达", deliveredAt);

        verify(deliveryService).recordDeliveryReceipt(
            ORDER_ID,
            "receipt-module.jpg",
            "已送达",
            deliveredAt,
            visibleAt,
            expiresAt
        );
        assertEquals("DELIVERED", jdbcTemplate.queryForObject(
            "SELECT item_status FROM dispatch_batch_items WHERE id = ?",
            String.class,
            BATCH_ITEM_ID
        ));
        assertEquals("receipt-module.jpg", jdbcTemplate.queryForObject(
            "SELECT reference_image_url FROM address_reference_images WHERE customer_address_id = ?",
            String.class,
            ADDRESS_ID
        ));
        assertEquals(ORDER_ID, jdbcTemplate.queryForObject(
            "SELECT source_order_id FROM address_reference_images WHERE customer_address_id = ?",
            Long.class,
            ADDRESS_ID
        ));
    }
}
