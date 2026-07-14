package com.jzqs.app.menu.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.menu.service.MenuWeekAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/menu-weeks")
public class MenuWeekAdminController {
    private final MenuWeekAdminService menuWeekAdminService;

    public MenuWeekAdminController(MenuWeekAdminService menuWeekAdminService) {
        this.menuWeekAdminService = menuWeekAdminService;
    }

    @GetMapping("/current")
    public ApiResponse<MenuWeekAdminResponse> current(@RequestParam(required = false) String targetDate) {
        return ApiResponse.success(
            targetDate == null || targetDate.isBlank()
                ? menuWeekAdminService.currentWeek()
                : menuWeekAdminService.weekByDate(targetDate)
        );
    }

    @PostMapping
    @RateLimit(key = "admin:menu-weeks:create-template", maxRequests = 2, windowSeconds = 15)
    @Idempotent(key = "admin:menu-weeks:create-template", ttlSeconds = 8, includeBody = false)
    @AuditAction(module = "MENU_WEEK", action = "CREATE_TEMPLATE")
    public ApiResponse<MenuWeekTemplateResponse> createNextWeekTemplate() {
        return ApiResponse.success(menuWeekAdminService.createNextWeekTemplate(AdminRequestContextSupport.requireOperatorName()));
    }

    @PutMapping("/{weekId}/days/{serveDate}")
    @RateLimit(key = "admin:menu-weeks:save-day", maxRequests = 6, windowSeconds = 10)
    @Idempotent(key = "admin:menu-weeks:save-day", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "MENU_WEEK", action = "SAVE_DAY")
    public ApiResponse<MenuWeekDaySaveResponse> saveDay(
        @PathVariable long weekId,
        @PathVariable String serveDate,
        @Valid @RequestBody MenuWeekDaySaveRequest request
    ) {
        return ApiResponse.success(menuWeekAdminService.saveDay(weekId, serveDate, request));
    }

    @PostMapping("/{weekId}/publish")
    @RateLimit(key = "admin:menu-weeks:publish", maxRequests = 2, windowSeconds = 15)
    @Idempotent(key = "admin:menu-weeks:publish", ttlSeconds = 10, includeBody = false)
    @AuditAction(module = "MENU_WEEK", action = "PUBLISH")
    public ApiResponse<MenuWeekPublishResponse> publish(@PathVariable long weekId) {
        return ApiResponse.success(menuWeekAdminService.publish(weekId, AdminRequestContextSupport.requireOperatorName()));
    }

    @PostMapping("/copy-from-last-week")
    @RateLimit(key = "admin:menu-weeks:copy-last-week", maxRequests = 2, windowSeconds = 15)
    @Idempotent(key = "admin:menu-weeks:copy-last-week", ttlSeconds = 10, includeBody = false)
    @AuditAction(module = "MENU_WEEK", action = "COPY_LAST_WEEK")
    public ApiResponse<MenuWeekCopyResponse> copyFromLastWeek() {
        return ApiResponse.success(menuWeekAdminService.copyFromLastWeek(AdminRequestContextSupport.requireOperatorName()));
    }
}
