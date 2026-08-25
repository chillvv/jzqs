package com.jzqs.app.delivery.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
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
    public DeliveryReceiptRecordResponse recordDeliveryReceipt(
        long orderId,
        String receiptUrl,
        String receiptNote,
        String deliveredAt,
        String visibleAt,
        String expiresAt
    ) {
        String status = jdbcTemplate.query(
            "SELECT status FROM meal_slot_orders WHERE id = ?",
            ps -> ps.setLong(1, orderId),
            rs -> rs.next() ? rs.getString("status") : null
        );
        if (status == null) {
            return new DeliveryReceiptRecordResponse(orderId, "NOT_FOUND", "SKIPPED", "SKIPPED", receiptUrl, visibleAt, expiresAt);
        }
        if ("CANCELLED".equals(status) || "REFUNDED".equals(status)) {
            return new DeliveryReceiptRecordResponse(orderId, status, "SKIPPED", "SKIPPED", receiptUrl, visibleAt, expiresAt);
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
            return new DeliveryReceiptRecordResponse(
                orderId,
                "DELIVERED",
                "UNCHANGED",
                "SKIPPED",
                receiptUrl,
                visibleAt,
                expiresAt
            );
        }
        insertAndReturnId(
            "INSERT INTO delivery_receipts (meal_slot_order_id, receipt_url, receipt_note, delivered_at, visible_at, expires_at, visible_to_customer) VALUES (?, ?, ?, ?, ?, ?, ?)",
            orderId, receiptUrl, receiptNote, deliveredTimestamp, visibleTimestamp, expiresTimestamp, visibleToCustomer
        );
        jdbcTemplate.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = ?", orderId);
        jdbcTemplate.update("UPDATE dispatch_assignments SET status = 'DELIVERED' WHERE meal_slot_order_id = ?", orderId);
        return new DeliveryReceiptRecordResponse(
            orderId,
            "DELIVERED",
            "ALREADY_CONSUMED",
            "SKIPPED",
            receiptUrl,
            visibleAt,
            expiresAt
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

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
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
