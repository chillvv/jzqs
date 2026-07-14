package com.jzqs.app.settings.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchAiAutoScheduleJob {
    private final SettingsService settingsService;

    public DispatchAiAutoScheduleJob(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void checkAndRun() {
        settingsService.runDispatchAiAutoSchedule();
    }
}
