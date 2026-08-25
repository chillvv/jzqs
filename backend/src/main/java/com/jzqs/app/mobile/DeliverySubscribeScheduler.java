package com.jzqs.app.mobile;

import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

    @Async("backgroundTaskExecutor")
    @Scheduled(cron = "0 * * * * ?")
    public void sendConfiguredNotifications() {
        OperationSettingsResponse settings = settingsService.operationSettings();
        if (!settings.deliverySubscribeEnabled()) {
            return;
        }
        // 每分钟扫描一次：仅在订单送达且已到餐期释放时间（午餐 11:30 / 晚餐 17:00）后发送，
        // 保证用户收到订阅消息时订单状态已是"已送达"、回执图片可见。
        int lunchCount = mobilePortalService.sendScheduledDeliverySubscribeMessages("LUNCH");
        int dinnerCount = mobilePortalService.sendScheduledDeliverySubscribeMessages("DINNER");
        if (lunchCount == 0 && dinnerCount == 0) {
            return;
        }
        log.info(
            "送达订阅通知扫描完成, lunchCount={}, dinnerCount={}",
            lunchCount,
            dinnerCount
        );
    }
}
