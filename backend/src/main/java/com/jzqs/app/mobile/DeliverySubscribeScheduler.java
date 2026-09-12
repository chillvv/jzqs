package com.jzqs.app.mobile;

import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
        scanAndSend();
    }

    /**
     * 应用启动后立即补扫一次：若发布/重启正好落在餐期释放时间之后，
     * 不必再等下一个整分钟，避免消息被无谓推迟。
     */
    @Async("backgroundTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void sendOnStartup() {
        scanAndSend();
    }

    private void scanAndSend() {
        OperationSettingsResponse settings = settingsService.operationSettings();
        if (!settings.deliverySubscribeEnabled()) {
            return;
        }
        // 每分钟扫描一次：仅在订单送达且已到餐期释放时间（后台可配，默认午餐 11:30 / 晚餐 17:30）后发送，
        // 保证用户收到订阅消息时订单状态已是"已送达"、回执图片可见。
        // 单轮发送带有时间预算与失败熔断（见 DeliverySubscriptionModule），不会长时间占住后台线程。
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
