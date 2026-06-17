package com.jzqs.app.mobile;

import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeliverySubscribeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliverySubscribeScheduler.class);

    private final MobilePortalService mobilePortalService;
    private final SettingsService settingsService;

    public DeliverySubscribeScheduler(MobilePortalService mobilePortalService, SettingsService settingsService) {
        this.mobilePortalService = mobilePortalService;
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendConfiguredNotifications() {
        OperationSettingsResponse settings = settingsService.operationSettings();
        if (!settings.deliverySubscribeEnabled()) {
            return;
        }
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        int lunchCount = 0;
        int dinnerCount = 0;
        if (matchesConfiguredTriggerTime(settings.deliverySubscribeLunchTime(), now)) {
            lunchCount = mobilePortalService.sendScheduledDeliverySubscribeMessages("LUNCH");
        }
        if (matchesConfiguredTriggerTime(settings.deliverySubscribeDinnerTime(), now)) {
            dinnerCount = mobilePortalService.sendScheduledDeliverySubscribeMessages("DINNER");
        }
        if (lunchCount == 0 && dinnerCount == 0) {
            return;
        }
        log.info(
            "送达订阅通知扫描完成, lunchTriggerTime={}, dinnerTriggerTime={}, lunchCount={}, dinnerCount={}",
            settings.deliverySubscribeLunchTime(),
            settings.deliverySubscribeDinnerTime(),
            lunchCount,
            dinnerCount
        );
    }

    private boolean matchesConfiguredTriggerTime(String configuredTime, LocalTime now) {
        try {
            return LocalTime.parse(configuredTime).withSecond(0).withNano(0).equals(now);
        } catch (Exception ex) {
            return false;
        }
    }
}
