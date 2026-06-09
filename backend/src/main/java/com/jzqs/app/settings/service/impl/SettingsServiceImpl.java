package com.jzqs.app.settings.service.impl;

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
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRootDir;

    public SettingsServiceImpl(JdbcTemplate jdbcTemplate, @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public OperationSettingsResponse operationSettings() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT ordering_enabled, holiday_notice_title, holiday_notice_desc, banner_images, popup_announcement_enabled, popup_announcement_content FROM admin_settings WHERE id = 1"
        );
        boolean orderingEnabled = Boolean.TRUE.equals(row.get("ordering_enabled"));
        String bannerImages = safeString(row.get("banner_images"));
        if (bannerImages.isEmpty()) {
            bannerImages = "[\"../../assets/hero-new.jpg\"]";
        }
        return new OperationSettingsResponse(
            orderingEnabled,
            orderingEnabled ? "通道开启中" : "暂停接单中",
            safeString(row.get("holiday_notice_title")),
            safeString(row.get("holiday_notice_desc")),
            orderingEnabled ? "熔断：一键暂停接单 (假期店休使用)" : "恢复：重新开启接单",
            bannerImages,
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
        jdbcTemplate.update("UPDATE admin_settings SET banner_images = ?, updated_at = ? WHERE id = 1", bannerImages, Timestamp.valueOf(LocalDateTime.now()));
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updatePopupAnnouncement(boolean enabled, String content) {
        jdbcTemplate.update("UPDATE admin_settings SET popup_announcement_enabled = ?, popup_announcement_content = ?, updated_at = ? WHERE id = 1", enabled, content, Timestamp.valueOf(LocalDateTime.now()));
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
            normalizedPopupContent = normalizedTitle + "\n" + normalizedDesc;
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
