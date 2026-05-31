package com.jzqs.app.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jzqs.app.settings.api.OperationSettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SettingsServiceImplTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNormalizeNullAdminSettingsFields() {
        jdbcTemplate.update(
            "UPDATE admin_settings SET holiday_notice_title = NULL, holiday_notice_desc = NULL, banner_images = NULL, popup_announcement_enabled = NULL, popup_announcement_content = NULL WHERE id = 1"
        );

        OperationSettingsResponse response = settingsService.operationSettings();

        assertEquals("", response.holidayNoticeTitle());
        assertEquals("", response.holidayNoticeDesc());
        assertEquals("[\"../../assets/hero-new.jpg\"]", response.bannerImages());
        assertFalse(response.popupAnnouncementEnabled());
        assertEquals("", response.popupAnnouncementContent());
    }
}
