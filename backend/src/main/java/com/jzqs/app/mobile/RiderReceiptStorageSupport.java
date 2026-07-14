package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class RiderReceiptStorageSupport {
    private static final long MAX_RECEIPT_UPLOAD_SIZE = 5L * 1024 * 1024;

    private final Path uploadRootDir;

    RiderReceiptStorageSupport(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file) {
        if (isBlank(riderName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "骑手姓名不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先选择回执图片");
        }
        if (file.getSize() > MAX_RECEIPT_UPLOAD_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "回执图片不能超过 5MB");
        }

        String extension = resolveUploadExtension(file.getOriginalFilename(), file.getContentType());
        LocalDate today = LocalDate.now();
        String fileName = sanitizeFileKey(riderName) + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        Path relativePath = Path.of("rider-receipts", today.toString(), fileName);
        Path targetPath = uploadRootDir.resolve(relativePath).normalize();

        ensureWithinUploadRoot(targetPath);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存回执图片失败", ex);
        }

        String storedPath = toPublicUploadPath(relativePath);
        return new RiderDeliveryUploadResponse(storedPath, storedPath, file.getSize());
    }

    String buildReceiptUrl(String receiptFileKey) {
        String normalized = isBlank(receiptFileKey) ? "" : receiptFileKey.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("cloud://")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/" + normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                String path = java.net.URI.create(normalized).getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    return path;
                }
            } catch (IllegalArgumentException ignored) {
                return normalized;
            }
        }
        return normalized;
    }

    void deleteManagedReceiptFileQuietly(String previousReceiptUrl, String nextReceiptUrl) {
        String previousPath = buildReceiptUrl(previousReceiptUrl);
        String nextPath = buildReceiptUrl(nextReceiptUrl);
        if (isBlank(previousPath) || previousPath.equals(nextPath) || !previousPath.startsWith("/uploads/")) {
            return;
        }

        Path relativePath = Path.of(previousPath.substring("/uploads/".length()));
        Path filePath = uploadRootDir.resolve(relativePath).normalize();
        ensureWithinUploadRoot(filePath);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Ignore stale file cleanup failures so receipt flow itself can continue.
        }
    }

    private String sanitizeFileKey(String riderName) {
        String base = isBlank(riderName) ? "rider" : riderName.trim();
        return base.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5_-]", "_");
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
