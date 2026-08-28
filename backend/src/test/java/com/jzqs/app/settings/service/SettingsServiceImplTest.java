package com.jzqs.app.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.mobile.MobilePortalService;
import com.jzqs.app.settings.api.OperationSettingsResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

@SpringBootTest
class SettingsServiceImplTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TransactionalRealtimePublisher realtimeEventPublisher;

    @MockBean
    private MobilePortalService mobilePortalService;

    @Test
    void shouldNormalizeNullAdminSettingsFields() {
        jdbcTemplate.update(
            "UPDATE admin_settings SET holiday_notice_title = '', holiday_notice_desc = '', banner_images = NULL, popup_announcement_enabled = FALSE, popup_announcement_content = '' WHERE id = 1"
        );

        OperationSettingsResponse response = settingsService.operationSettings();

        assertEquals("", response.holidayNoticeTitle());
        assertEquals("", response.holidayNoticeDesc());
        assertEquals(
            "[{\"imageUrl\":\"../../assets/hero-new.jpg\",\"title\":\"\",\"description\":\"\",\"enabled\":true}]",
            response.bannerImages()
        );
        assertFalse(response.popupAnnouncementEnabled());
        assertEquals("", response.popupAnnouncementContent());
    }

    @Test
    void shouldKeepBannerActionFieldsWhenNormalizingStoredConfig() {
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET banner_images = ?
                WHERE id = 1
                """,
            """
                [{"imageUrl":"/uploads/settings-banners/test.jpg","title":"新品","description":"轻食上新","enabled":true,"actionType":"MINI_PROGRAM_PAGE","actionTarget":"pages/order/index"}]
                """
        );

        OperationSettingsResponse response = settingsService.operationSettings();

        assertTrue(response.bannerImages().contains("\"imageUrl\":\"/uploads/settings-banners/test.jpg\""));
        assertTrue(response.bannerImages().contains("\"enabled\":true"));
        assertFalse(response.bannerImages().contains("\"actionType\""));
        assertFalse(response.bannerImages().contains("\"actionTarget\""));
    }

    @Test
    void shouldPublishAnnouncementEventWhenUpdatingOrderingToggle() {
        settingsService.updateOrderingEnabled(false);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("system.announcement.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("customer:all"));
    }

    @Test
    void shouldPublishHomeEventWhenUpdatingBannerImages() {
        settingsService.updateBannerImages("[\"/uploads/settings-banners/new.jpg\"]", 5);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("system.home.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("customer:all"));
    }

    @Test
    void shouldExposePackageReminderSettings() {
        jdbcTemplate.update(
            "UPDATE admin_settings SET package_expiry_reminder_days = 9, package_low_balance_threshold = 4 WHERE id = 1"
        );

        OperationSettingsResponse response = settingsService.operationSettings();

        assertEquals(9, response.packageExpiryReminderDays());
        assertEquals(4, response.packageLowBalanceThreshold());
    }

    @Test
    void shouldFlushDeliveredSubscriptionsWhenSwitchingFromFixedTimeToImmediateMode() {
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET delivery_subscribe_enabled = TRUE,
                    delivery_subscribe_lunch_time = '11:30',
                    delivery_subscribe_dinner_time = '17:30'
                WHERE id = 1
                """
        );

        settingsService.updatePackageReminderSettings(7, 3, true, false, "11:30", "17:30", false, "", "", "");

        verify(mobilePortalService).sendAllDeliveredPendingSubscriptions();
    }

    @Test
    void shouldExposeUpdatedDispatchAiAnchorNameFromWorkbench() {
        var response = settingsService.updateDispatchAiSettings(
            true,
            "00:05",
            "NEAR_TO_FAR",
            "软件园北门",
            "高新区软件园北门",
            true,
            "https://api.deepseek.com",
            "",
            "deepseek-chat",
            "请按区域记忆优化排线",
            "20.00",
            "测试员"
        );

        assertEquals("软件园北门", response.settings().anchorName());
    }

    @Test
    void shouldExposeUpdatedDispatchAiEnabledFlagFromWorkbench() {
        var response = settingsService.updateDispatchAiSettings(
            true,
            "00:05",
            "NEAR_TO_FAR",
            "软件园北门",
            "高新区软件园北门",
            false,
            "https://api.deepseek.com",
            "",
            "deepseek-chat",
            "请按区域记忆优化排线",
            "20.00",
            "测试员"
        );

        assertFalse(response.settings().aiEnabled());
    }

    @Test
    void shouldMarkStaleRouteLabLogsAsFailedWhenLoadingWorkbench() {
        long logId = insertDispatchAiJobLog(
            "TEST_LAB",
            "RUNNING",
            "{\"existing\":\"value\"}",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
            null
        );

        var response = settingsService.dispatchAiWorkbench();
        var log = response.recentLogs().stream()
            .filter(item -> item.id() == logId)
            .findFirst()
            .orElseThrow();

        assertEquals("TEST", log.runType());
        assertEquals("FAILED", log.status());
        assertFalse(log.message().isBlank());
        assertTrue(log.metadataJson().contains("\"existing\":\"value\""));
        assertTrue(log.metadataJson().contains("\"thinkingStatus\":\"FAILED\""));
        assertTrue(log.metadataJson().contains("\"currentPhase\":\"\u6267\u884c\u4e2d\u65ad\""));
        assertTrue(log.metadataJson().contains("\"thinkingHeadline\":\"\u5386\u53f2\u4efb\u52a1\u672a\u6b63\u786e\u6536\u5c3e\""));
        assertFalse(log.finishedAt().isBlank());

        var stored = jdbcTemplate.queryForMap(
            "SELECT status, message, finished_at FROM dispatch_ai_job_logs WHERE id = ?",
            logId
        );
        assertEquals("FAILED", stored.get("status"));
        assertEquals(log.message(), stored.get("message"));
        assertNotNull(stored.get("finished_at"));
    }

    @Test
    void shouldKeepStaleProductionLogsRunningWhenLoadingWorkbench() {
        long logId = insertDispatchAiJobLog(
            "MANUAL_RUN",
            "RUNNING",
            "{\"existing\":\"production\"}",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
            null
        );

        var response = settingsService.dispatchAiWorkbench();
        var log = response.recentLogs().stream()
            .filter(item -> item.id() == logId)
            .findFirst()
            .orElseThrow();

        assertEquals("PRODUCTION", log.runType());
        assertEquals("RUNNING", log.status());
        assertEquals("{\"existing\":\"production\"}", log.metadataJson());
        assertTrue(log.finishedAt() == null || log.finishedAt().isBlank());

        var stored = jdbcTemplate.queryForMap(
            "SELECT status, finished_at FROM dispatch_ai_job_logs WHERE id = ?",
            logId
        );
        assertEquals("RUNNING", stored.get("status"));
        assertEquals(null, stored.get("finished_at"));
    }

    private long insertDispatchAiJobLog(
        String triggerSource,
        String status,
        String metadataJson,
        Timestamp startedAt,
        Timestamp finishedAt
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                    INSERT INTO dispatch_ai_job_logs (
                        trigger_source,
                        status,
                        metadata_json,
                        executed_by,
                        started_at,
                        finished_at
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, triggerSource);
            statement.setString(2, status);
            statement.setString(3, metadataJson);
            statement.setString(4, "test");
            statement.setTimestamp(5, startedAt);
            statement.setTimestamp(6, finishedAt);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
