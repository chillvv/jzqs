package com.jzqs.app.menu.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.menu.service.MenuScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/admin/menu-schedules")
public class MenuScheduleController {
    private final MenuScheduleService menuScheduleService;

    public MenuScheduleController(MenuScheduleService menuScheduleService) {
        this.menuScheduleService = menuScheduleService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MenuScheduleResponse>> list() {
        return ApiResponse.success(menuScheduleService.list());
    }

    @PostMapping
    @RateLimit(key = "admin:menu-schedules:create", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:menu-schedules:create", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "MENU_SCHEDULE", action = "CREATE")
    public ApiResponse<MenuScheduleUpsertResponse> create(@Valid @RequestBody MenuScheduleUpsertRequest request) {
        return ApiResponse.success(menuScheduleService.create(
            request.serveDate(),
            request.mealPeriod(),
            request.mealName(),
            request.mealDetail(),
            request.calories(),
            request.merchantNote()
        ));
    }

    @PutMapping("/{id}")
    @RateLimit(key = "admin:menu-schedules:update", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:menu-schedules:update", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "MENU_SCHEDULE", action = "UPDATE")
    public ApiResponse<MenuScheduleUpsertResponse> update(@PathVariable long id, @Valid @RequestBody MenuScheduleUpsertRequest request) {
        return ApiResponse.success(menuScheduleService.update(
            id,
            request.serveDate(),
            request.mealPeriod(),
            request.mealName(),
            request.mealDetail(),
            request.calories(),
            request.merchantNote()
        ));
    }

    @PostMapping("/{id}/disable")
    @RateLimit(key = "admin:menu-schedules:disable", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:menu-schedules:disable", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "MENU_SCHEDULE", action = "DISABLE")
    public ApiResponse<MenuScheduleStatusResponse> disable(@PathVariable long id) {
        return ApiResponse.success(menuScheduleService.disable(id));
    }
}
