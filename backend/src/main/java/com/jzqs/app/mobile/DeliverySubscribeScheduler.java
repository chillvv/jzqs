package com.jzqs.app.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeliverySubscribeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliverySubscribeScheduler.class);

    private final MobilePortalService mobilePortalService;

    public DeliverySubscribeScheduler(MobilePortalService mobilePortalService) {
        this.mobilePortalService = mobilePortalService;
    }

    @Scheduled(cron = "0 30 11 * * ?")
    public void sendLunchNotifications() {
        int count = mobilePortalService.sendScheduledDeliverySubscribeMessages("LUNCH");
        log.info("午餐送达订阅通知扫描完成: {}", count);
    }

    @Scheduled(cron = "0 0 17 * * ?")
    public void sendDinnerNotifications() {
        int count = mobilePortalService.sendScheduledDeliverySubscribeMessages("DINNER");
        log.info("晚餐送达订阅通知扫描完成: {}", count);
    }
}
