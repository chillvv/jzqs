package com.jzqs.app.delivery.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.delivery.api.DeliveryReceiptUploadResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private static final long MAX_RECEIPT_UPLOAD_SIZE = 5L * 1024 * 1024;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRootDir;

    public DeliveryServiceImpl(
        JdbcTemplate jdbcTemplate,
        @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public DeliveryReceiptUploadResponse uploadReceiptImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先选择回执图片");
        }
        if (file.getSize() > MAX_RECEIPT_UPLOAD_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "回执图片不能超过 5MB");
        }

        String extension = resolveUploadExtension(file.getOriginalFilename(), file.getContentType());
        LocalDate today = LocalDate.now();
        String fileName = "admin-receipt-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        Path relativePath = Path.of("admin-receipts", today.toString(), fileName);
        Path targetPath = uploadRootDir.resolve(relativePath).normalize();
        ensureWithinUploadRoot(targetPath);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存回执图片失败", ex);
        }

        String publicPath = toPublicUploadPath(relativePath);
        return new DeliveryReceiptUploadResponse(publicPath, publicPath, file.getSize());
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
        Timestamp deliveredTimestamp = parseTimestamp(deliveredAt, "送达时间格式不正确");
        Timestamp visibleTimestamp = parseTimestamp(visibleAt, "可见时间格式不正确");
        Timestamp expiresTimestamp = parseTimestamp(expiresAt, "过期时间格式不正确");
        boolean visibleToCustomer = visibleTimestamp != null && !visibleTimestamp.after(Timestamp.valueOf(LocalDateTime.now()));
        Long latestReceiptId = jdbcTemplate.query(
            """
                SELECT id
                FROM delivery_receipts
                WHERE meal_slot_order_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            ps -> ps.setLong(1, orderId),
            rs -> rs.next() ? rs.getLong("id") : null
        );
        if (latestReceiptId != null) {
            jdbcTemplate.update(
                """
                    UPDATE delivery_receipts
                    SET receipt_url = ?,
                        receipt_note = ?,
                        delivered_at = ?,
                        visible_at = ?,
                        expires_at = ?,
                        visible_to_customer = ?
                    WHERE meal_slot_order_id = ?
                    """,
                receiptUrl,
                receiptNote,
                deliveredTimestamp,
                visibleTimestamp,
                expiresTimestamp,
                visibleToCustomer,
                orderId
            );
            jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = ?", orderId);
            jdbcTemplate.update("UPDATE dispatch_assignments SET status = 'DELIVERED' WHERE meal_slot_order_id = ?", orderId);
            return Map.of(
                "mealSlotOrderId", orderId,
                "orderStatus", "DELIVERED",
                "walletAction", "UNCHANGED",
                "notificationStatus", "SKIPPED",
                "receiptUrl", receiptUrl,
                "visibleAt", visibleAt,
                "expiresAt", expiresAt
            );
        }
        insertAndReturnId(
            "INSERT INTO delivery_receipts (meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, expires_at, visible_to_customer) VALUES (?, ?, ?, ?, ?, ?, ?)",
            orderId, receiptUrl, receiptNote, deliveredTimestamp, visibleTimestamp, expiresTimestamp, visibleToCustomer
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = ?", orderId);
        jdbcTemplate.update("UPDATE dispatch_assignments SET status = 'DELIVERED' WHERE meal_slot_order_id = ?", orderId);
        Map<String, Object> walletInfo = jdbcTemplate.query(
            """
                SELECT mw.id AS wallet_id, COALESCE(mso.quantity, 1) AS quantity
                FROM meal_slot_orders mso
                LEFT JOIN daily_orders do ON do.id = mso.daily_order_id
                LEFT JOIN meal_wallets mw
                    ON mw.customer_id = do.customer_id
                   AND mw.active = TRUE
                   AND (mw.expired_at IS NULL OR mw.expired_at >= CURRENT_TIMESTAMP)
                WHERE mso.id = ?
                ORDER BY mw.id DESC
                LIMIT 1
                """,
            ps -> ps.setLong(1, orderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                long walletIdValue = rs.getLong("wallet_id");
                row.put("walletId", rs.wasNull() ? null : walletIdValue);
                row.put("quantity", rs.getInt("quantity"));
                return row;
            }
        );
        Long walletId = walletInfo == null ? null : (Long) walletInfo.get("walletId");
        int quantity = walletInfo == null ? 1 : ((Number) walletInfo.get("quantity")).intValue();
        String walletAction = "SKIPPED";
        if (walletId != null) {
            jdbcTemplate.update(
                "UPDATE meal_wallets SET reserved_meals = CASE WHEN reserved_meals >= ? THEN reserved_meals - ? ELSE 0 END, consumed_meals = consumed_meals + ? WHERE id = ?",
                quantity,
                quantity,
                quantity,
                walletId
            );
            insertWalletTransaction(walletId, "CONSUME", -quantity, "系统", "送达后核销餐次", orderId);
            walletAction = "CONSUMED";
        }
        return Map.of(
            "mealSlotOrderId", orderId,
            "orderStatus", "DELIVERED",
            "walletAction", walletAction,
            "notificationStatus", "SKIPPED",
            "receiptUrl", receiptUrl,
            "visibleAt", visibleAt,
            "expiresAt", expiresAt
        );
    }

    private Timestamp parseTimestamp(String rawValue, String errorMessage) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(rawValue));
        } catch (DateTimeParseException ignored) {
            // Fallback to ISO-8601 timestamps from browser/client code, e.g. 2026-06-15T12:34:56.789Z.
        }
        try {
            return Timestamp.from(Instant.parse(rawValue));
        } catch (DateTimeParseException ignored) {
            // Some clients may send offset timestamps without trailing Z.
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(rawValue).toInstant());
        } catch (DateTimeParseException ignored) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
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

    private String resolveUploadExtension(String originalFilename, String contentType) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        if (lowerContentType.contains("png")) {
            return ".png";
        }
        if (lowerContentType.contains("webp")) {
            return ".webp";
        }
        if (lowerContentType.contains("gif")) {
            return ".gif";
        }
        String normalizedName = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        if (normalizedName.endsWith(".png")) {
            return ".png";
        }
        if (normalizedName.endsWith(".webp")) {
            return ".webp";
        }
        if (normalizedName.endsWith(".gif")) {
            return ".gif";
        }
        return ".jpg";
    }

    private void ensureWithinUploadRoot(Path targetPath) {
        if (!targetPath.startsWith(uploadRootDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的图片存储路径");
        }
    }

    private String toPublicUploadPath(Path relativePath) {
        return "/uploads/" + relativePath.toString().replace('\\', '/');
    }
}
