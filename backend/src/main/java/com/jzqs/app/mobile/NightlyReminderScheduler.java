package com.jzqs.app.mobile;

import com.jzqs.app.settings.service.SettingsService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NightlyReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(NightlyReminderScheduler.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final NightlyReminderModule nightlyReminderModule;
    private final SettingsService settingsService;

    private final AtomicReference<LocalDate> lastSentDate = new AtomicReference<>(null);

    public NightlyReminderScheduler(NightlyReminderModule nightlyReminderModule, SettingsService settingsService) {
        this.nightlyReminderModule = nightlyReminderModule;
        this.settingsService = settingsService;
    }

    /**
     * 每分钟轮询一次：仅当当前上海时间落在后台配置的发送时间（精确到分钟），
     * 且当天尚未发送过时，才触发每晚提醒。
     * 发送时间由后台「每晚提醒时间」(nightlyReminderTime) 配置控制，不写死。
     * 是否真正发送由 NightlyReminderModule 判断：需开启总开关、且明天有菜单排期（营业）。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void pollNightlyReminders() {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalTime now = LocalTime.now(APP_ZONE);
        LocalTime target = LocalTime.parse(settingsService.operationSettings().nightlyReminderTime());
        if (now.getHour() != target.getHour() || now.getMinute() != target.getMinute()) {
            return;
        }
        if (today.equals(lastSentDate.get())) {
            return;
        }
        lastSentDate.set(today);
        int count = nightlyReminderModule.sendNightlyReminders();
        log.info("[每晚提醒] 已触发, target={}, now={}, count={}", target, now, count);
    }
}
