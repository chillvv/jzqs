package com.jzqs.app.settings.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.settings.api.BannerImageUploadResponse;
import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SettingsServiceImpl implements SettingsService {
    private static final long MAX_BANNER_UPLOAD_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_BANNER_IMAGE = "../../assets/hero-new.jpg";
    private static final int DEFAULT_BANNER_INTERVAL_SECONDS = 3;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path uploadRootDir;

    public SettingsServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public OperationSettingsResponse operationSettings() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT ordering_enabled, holiday_notice_title, holiday_notice_desc, banner_images, banner_interval_seconds, popup_announcement_enabled, popup_announcement_content FROM admin_settings WHERE id = 1"
        );
        boolean orderingEnabled = Boolean.TRUE.equals(row.get("ordering_enabled"));
        return new OperationSettingsResponse(
            orderingEnabled,
            orderingEnabled ? "通道开启中" : "暂停接单中",
            safeString(row.get("holiday_notice_title")),
            safeString(row.get("holiday_notice_desc")),
            orderingEnabled ? "熔断：一键暂停接单 (假期店休使用)" : "恢复：重新开启接单",
            normalizeStoredBannerImages(safeString(row.get("banner_images"))),
            normalizeBannerIntervalSeconds(row.get("banner_interval_seconds")),
            Boolean.TRUE.equals(row.get("popup_announcement_enabled")),
            safeString(row.get("popup_announcement_content"))
        );
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateOrderingEnabled(boolean enabled) {
        jdbcTemplate.update("UPDATE admin_settings SET ordering_enabled = ?, updated_at = ? WHERE id = 1", enabled, Timestamp.valueOf(LocalDateTime.now()));
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateHolidayNotice(String title, String desc) {
        jdbcTemplate.update("UPDATE admin_settings SET holiday_notice_title = ?, holiday_notice_desc = ?, updated_at = ? WHERE id = 1", title, desc, Timestamp.valueOf(LocalDateTime.now()));
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateBannerImages(String bannerImages) {
        return updateBannerImages(bannerImages, DEFAULT_BANNER_INTERVAL_SECONDS);
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateBannerImages(String bannerImages, int bannerIntervalSeconds) {
        jdbcTemplate.update(
            "UPDATE admin_settings SET banner_images = ?, banner_interval_seconds = ?, updated_at = ? WHERE id = 1",
            normalizeStoredBannerImages(bannerImages),
            Math.max(1, bannerIntervalSeconds),
            Timestamp.valueOf(LocalDateTime.now())
        );
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updatePopupAnnouncement(String title, String desc, boolean enabled, String content) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDesc = desc == null ? "" : desc.trim();
        String normalizedContent = content == null ? "" : content.trim();
        if (enabled && normalizedContent.isEmpty()) {
            normalizedContent = buildPopupContent(normalizedTitle, normalizedDesc);
        }
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET holiday_notice_title = ?,
                    holiday_notice_desc = ?,
                    popup_announcement_enabled = ?,
                    popup_announcement_content = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            normalizedTitle,
            normalizedDesc,
            enabled,
            enabled ? normalizedContent : "",
            Timestamp.valueOf(LocalDateTime.now())
        );
        return operationSettings();
    }

    @Override
    public BannerImageUploadResponse uploadBannerImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先选择轮播图图片");
        }
        if (file.getSize() > MAX_BANNER_UPLOAD_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "轮播图图片不能超过 5MB");
        }
        String extension = resolveUploadExtension(file.getOriginalFilename(), file.getContentType());
        LocalDate today = LocalDate.now();
        String fileName = "banner-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        Path relativePath = Path.of("settings-banners", today.toString(), fileName);
        Path targetPath = uploadRootDir.resolve(relativePath).normalize();
        ensureWithinUploadRoot(targetPath);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存轮播图图片失败", ex);
        }
        String publicPath = toPublicUploadPath(relativePath);
        return new BannerImageUploadResponse(publicPath, publicPath, file.getSize());
    }

    @Override
    @Transactional
    public OperationSettingsResponse pauseOrderingWithNotice(String title, String desc, boolean popupEnabled, String popupContent) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDesc = desc == null ? "" : desc.trim();
        String normalizedPopupContent = popupContent == null ? "" : popupContent.trim();
        if (popupEnabled && normalizedPopupContent.isEmpty()) {
            normalizedPopupContent = buildPopupContent(normalizedTitle, normalizedDesc);
        }
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET ordering_enabled = FALSE,
                    holiday_notice_title = ?,
                    holiday_notice_desc = ?,
                    popup_announcement_enabled = ?,
                    popup_announcement_content = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            normalizedTitle,
            normalizedDesc,
            popupEnabled,
            popupEnabled ? normalizedPopupContent : "",
            Timestamp.valueOf(LocalDateTime.now())
        );
        return operationSettings();
    }

    private String buildPopupContent(String title, String desc) {
        if (title.isBlank()) {
            return desc;
        }
        if (desc.isBlank()) {
            return title;
        }
        return title + "\n" + desc;
    }

    private String normalizeStoredBannerImages(String rawBannerImages) {
        String normalized = rawBannerImages == null ? "" : rawBannerImages.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return defaultBannerImagesJson();
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            if (!root.isArray() || root.isEmpty()) {
                return defaultBannerImagesJson();
            }
            ArrayNode normalizedArray = objectMapper.createArrayNode();
            for (JsonNode node : root) {
                if (node == null || node.isNull()) {
                    continue;
                }
                if (node.isTextual()) {
                    String imageUrl = node.asText("").trim();
                    if (!imageUrl.isEmpty()) {
                        normalizedArray.add(createBannerNode(imageUrl, true));
                    }
                    continue;
                }
                if (!node.isObject()) {
                    continue;
                }
                String imageUrl = node.path("imageUrl").asText("").trim();
                if (imageUrl.isEmpty()) {
                    imageUrl = node.path("url").asText("").trim();
                }
                if (imageUrl.isEmpty()) {
                    continue;
                }
                boolean enabled = !node.has("enabled") || node.path("enabled").asBoolean(true);
                normalizedArray.add(createBannerNode(imageUrl, enabled));
            }
            return normalizedArray.isEmpty() ? defaultBannerImagesJson() : objectMapper.writeValueAsString(normalizedArray);
        } catch (JsonProcessingException ex) {
            return defaultBannerImagesJson();
        }
    }

    private ObjectNode createBannerNode(
        String imageUrl,
        boolean enabled
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("imageUrl", imageUrl);
        node.put("enabled", enabled);
        return node;
    }

    private String defaultBannerImagesJson() {
        try {
            return objectMapper.writeValueAsString(
                objectMapper.createArrayNode().add(createBannerNode(DEFAULT_BANNER_IMAGE, true))
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("默认轮播图配置序列化失败", ex);
        }
    }

    private int normalizeBannerIntervalSeconds(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(safeString(value).trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_BANNER_INTERVAL_SECONDS;
        }
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
