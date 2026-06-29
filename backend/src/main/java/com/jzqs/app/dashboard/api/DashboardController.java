package com.jzqs.app.dashboard.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.dashboard.service.DashboardService;
import com.jzqs.app.subscription.api.LowBalanceSubscriptionItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/low-balance-subscriptions")
    public ApiResponse<List<LowBalanceSubscriptionItem>> lowBalanceSubscriptions() {
        return ApiResponse.success(dashboardService.lowBalanceSubscriptions());
    }
}
