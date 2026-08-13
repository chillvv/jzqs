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
     * 是否真正发送由 NightlyReminderModule 判断：需开启总开关、且明天有菜单排期（营业）。
     */
    /**
     * 每分钟轮询一次，发送每晚提醒（优惠券过期提醒）。
     * 是否真正发送由 NightlyReminderModule 判断：需开启总开关、且明天有菜单排期（营业）。
     *
     * ！！临时测试态：为验证订阅消息可用性，已放开「时:分时间窗口」与「当天去重」，
     * cron 每分钟触发即尝试发送一次。生产须还原为「时:分匹配 + 当天去重」逻辑
     * （见下方注释中的判断）。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void pollNightlyReminders() {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalTime now = LocalTime.now(APP_ZONE);
        // ===== 临时测试态：每分钟都发，去掉时间窗口与当天去重 =====
        // LocalTime target = LocalTime.parse(settingsService.operationSettings().nightlyReminderTime());
        // if (now.getHour() != target.getHour() || now.getMinute() != target.getMinute()) { return; }
        // if (today.equals(lastSentDate.get())) { return; }
        // lastSentDate.set(today);
        int count = nightlyReminderModule.sendNightlyReminders();
        log.info("[测试态] 每晚提醒每分钟触发, now={}, count={}", now, count);
    }
}
