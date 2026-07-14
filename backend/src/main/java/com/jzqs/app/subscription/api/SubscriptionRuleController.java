package com.jzqs.app.subscription.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.subscription.service.SubscriptionRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @RateLimit(key = "admin:subscription-rules:create", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:subscription-rules:create", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "SUBSCRIPTION_RULE", action = "CREATE")
    public ApiResponse<SubscriptionRuleResponse> createRule(@Valid @RequestBody SubscriptionRuleRequest request) {
        return ApiResponse.success(subscriptionRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    @RateLimit(key = "admin:subscription-rules:update", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:subscription-rules:update", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "SUBSCRIPTION_RULE", action = "UPDATE")
    public ApiResponse<SubscriptionRuleResponse> updateRule(
        @PathVariable long id,
        @Valid @RequestBody SubscriptionRuleRequest request
    ) {
        return ApiResponse.success(subscriptionRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @RateLimit(key = "admin:subscription-rules:delete", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:subscription-rules:delete", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "SUBSCRIPTION_RULE", action = "DELETE")
    public ApiResponse<SubscriptionRuleDeleteResponse> deleteRule(@PathVariable long id) {
        return ApiResponse.success(subscriptionRuleService.deleteRule(id));
    }

    @PostMapping("/{id}/toggle")
    @RateLimit(key = "admin:subscription-rules:toggle", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:subscription-rules:toggle", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "SUBSCRIPTION_RULE", action = "TOGGLE")
    public ApiResponse<SubscriptionRuleTogglePauseResponse> togglePause(@PathVariable long id) {
        return ApiResponse.success(subscriptionRuleService.togglePause(id));
    }
}
