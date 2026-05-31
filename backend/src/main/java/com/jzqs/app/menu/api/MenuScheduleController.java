package com.jzqs.app.menu.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.service.MenuScheduleService;
import jakarta.validation.Valid;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody MenuScheduleUpsertRequest request) {
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
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @Valid @RequestBody MenuScheduleUpsertRequest request) {
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
    public ApiResponse<Map<String, Object>> disable(@PathVariable long id) {
        return ApiResponse.success(menuScheduleService.disable(id));
    }
}
