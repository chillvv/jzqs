package com.jzqs.app.menu.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.menu.service.MenuWeekAdminService;
import jakarta.validation.Valid;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> createNextWeekTemplate() {
        return ApiResponse.success(menuWeekAdminService.createNextWeekTemplate("system"));
    }

    @PutMapping("/{weekId}/days/{serveDate}")
    public ApiResponse<Map<String, Object>> saveDay(
        @PathVariable long weekId,
        @PathVariable String serveDate,
        @Valid @RequestBody MenuWeekDaySaveRequest request
    ) {
        return ApiResponse.success(menuWeekAdminService.saveDay(weekId, serveDate, request));
    }

    @PostMapping("/{weekId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long weekId) {
        return ApiResponse.success(menuWeekAdminService.publish(weekId, "system"));
    }

    @PostMapping("/copy-from-last-week")
    public ApiResponse<Map<String, Object>> copyFromLastWeek() {
        return ApiResponse.success(menuWeekAdminService.copyFromLastWeek("system"));
    }
}
