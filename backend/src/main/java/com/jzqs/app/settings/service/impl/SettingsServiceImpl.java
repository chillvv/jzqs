package com.jzqs.app.settings.service.impl;

import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsServiceImpl implements SettingsService {
    private final JdbcTemplate jdbcTemplate;

    public SettingsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
