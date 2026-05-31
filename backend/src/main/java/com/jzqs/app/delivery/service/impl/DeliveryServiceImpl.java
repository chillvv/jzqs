package com.jzqs.app.delivery.service.impl;

import com.jzqs.app.delivery.service.DeliveryService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private final JdbcTemplate jdbcTemplate;

    public DeliveryServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Map<String, Object> recordDeliveryReceipt(
        long orderId,
        String receiptUrl,
        String receiptNote,
        String deliveredAt,
        String visibleAt,
        String expiresAt
    ) {
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meal_slot_orders WHERE id = ?", Integer.class, orderId);
        if (exists == null || exists == 0) {
            return Map.of("mealSlotOrderId", orderId, "status", "NOT_FOUND");
        }
        Timestamp deliveredTimestamp = Timestamp.valueOf(LocalDateTime.parse(deliveredAt));
        Timestamp visibleTimestamp = visibleAt == null ? null : Timestamp.valueOf(LocalDateTime.parse(visibleAt));
        Timestamp expiresTimestamp = expiresAt == null ? null : Timestamp.valueOf(LocalDateTime.parse(expiresAt));
        insertAndReturnId(
            "INSERT INTO delivery_receipts (meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, expires_at, visible_to_customer) VALUES (?, ?, ?, ?, ?, ?, ?)",
            orderId, receiptUrl, receiptNote, deliveredTimestamp, visibleTimestamp, expiresTimestamp,
            visibleTimestamp != null && !visibleTimestamp.after(Timestamp.valueOf(LocalDateTime.now()))
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = ?", orderId);
        jdbcTemplate.update("UPDATE dispatch_assignments SET status = 'DELIVERED' WHERE meal_slot_order_id = ?", orderId);
        Long walletId = jdbcTemplate.queryForObject("""
            SELECT mw.id
            FROM meal_wallets mw
            JOIN daily_orders do ON do.customer_id = mw.customer_id
            JOIN meal_slot_orders mso ON mso.daily_order_id = do.id
            WHERE mso.id = ? AND mw.active = TRUE
            """, Long.class, orderId);
        jdbcTemplate.update(
            "UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals > 0 THEN reserved_meals - 1 ELSE 0 END, consumed_meals = consumed_meals + 1 WHERE id = ?",
            walletId
        );
        insertWalletTransaction(walletId, "CONSUME", -1, "系统", "送达后核销餐次", orderId);
        Long customerId = jdbcTemplate.queryForObject("SELECT do.customer_id FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE mso.id = ?", Long.class, orderId);
        insertNotification(customerId, "DELIVERY_SUCCESS", "{\"content\":\"订单已送达\"}");
        return Map.of(
            "mealSlotOrderId", orderId,
            "orderStatus", "DELIVERED",
            "walletAction", "CONSUMED",
            "notificationStatus", "SENT",
            "receiptUrl", receiptUrl,
            "visibleAt", visibleAt,
            "expiresAt", expiresAt
        );
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark, Long relatedOrderId) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, remark, created_at, related_order_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark,
            relatedOrderId
        );
    }

    private void insertNotification(long customerId, String templateCode, String payloadJson) {
        jdbcTemplate.update(
            "INSERT INTO notification_logs (customer_id, channel, template_code, payload_json, sent_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
            customerId,
            "WECHAT",
            templateCode,
            payloadJson
        );
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idValue = keys.containsKey("ID") ? keys.get("ID") : keys.get("id");
            if (idValue == null) {
                idValue = keys.values().iterator().next();
            }
            if (idValue instanceof Number number) {
                return number.longValue();
            }
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        return 0L;
    }
}
