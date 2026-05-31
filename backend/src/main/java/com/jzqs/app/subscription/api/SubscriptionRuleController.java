package com.jzqs.app.subscription.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.subscription.service.SubscriptionRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/subscription-rules")
public class SubscriptionRuleController {
    private final SubscriptionRuleService subscriptionRuleService;

    public SubscriptionRuleController(SubscriptionRuleService subscriptionRuleService) {
        this.subscriptionRuleService = subscriptionRuleService;
    }

    @GetMapping
    public ApiResponse<List<SubscriptionRuleResponse>> listRules(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        return ApiResponse.success(subscriptionRuleService.listRules(keyword, status));
    }

    @PostMapping
    public ApiResponse<SubscriptionRuleResponse> createRule(@Valid @RequestBody SubscriptionRuleRequest request) {
        return ApiResponse.success(subscriptionRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubscriptionRuleResponse> updateRule(
        @PathVariable long id,
        @Valid @RequestBody SubscriptionRuleRequest request
    ) {
        return ApiResponse.success(subscriptionRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteRule(@PathVariable long id) {
        return ApiResponse.success(subscriptionRuleService.deleteRule(id));
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Map<String, Object>> togglePause(@PathVariable long id) {
        return ApiResponse.success(subscriptionRuleService.togglePause(id));
    }
}
