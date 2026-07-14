package com.jzqs.app.aftersale.api;

import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/aftersales")
public class AftersaleController {
    private final AftersaleService aftersaleService;

    public AftersaleController(AftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @GetMapping
    public ApiResponse<List<AdminAftersaleListItemResponse>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @RequestParam(required = false) String view,
        @RequestParam(required = false) Boolean hideAutoRefund
    ) {
        return ApiResponse.success(
            aftersaleService.listCases(status, type, startDate, endDate, view, hideAutoRefund)
        );
    }

    @GetMapping("/order-options")
    public ApiResponse<List<AdminAftersaleOrderOptionResponse>> orderOptions(@RequestParam String serveDate) {
        return ApiResponse.success(aftersaleService.orderOptions(serveDate));
    }

    @PostMapping
    @RateLimit(key = "admin:aftersales:create", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:aftersales:create", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "AFTERSALE", action = "CREATE")
    public ApiResponse<AdminAftersaleCreateResponse> create(@Valid @RequestBody AdminAftersaleCreateRequest body) {
        return ApiResponse.success(aftersaleService.createCase(new AdminAftersaleCreateRequest(
            body.orderId(),
            body.type(),
            body.reasonCode(),
            body.reasonText(),
            body.issueParamSummary(),
            body.estimatedLossMeals(),
            body.sourceCategory(),
            body.remark(),
            AdminRequestContextSupport.requireOperatorName()
        )));
    }

    @PostMapping("/{caseId}/resolve")
    @RateLimit(key = "admin:aftersales:resolve", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:aftersales:resolve", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "AFTERSALE", action = "RESOLVE")
    public ApiResponse<AdminAftersaleResolveResponse> resolve(@PathVariable long caseId, @Valid @RequestBody AdminAftersaleResolveRequest body) {
        return ApiResponse.success(aftersaleService.resolveCase(caseId, new AdminAftersaleResolveRequest(
            body.resolutionAction(),
            body.refundBlocking(),
            body.walletDelta(),
            body.settledLossMeals(),
            body.giftZeroMealCount(),
            body.giftVeggieJuiceCount(),
            body.adminRemark(),
            AdminRequestContextSupport.requireOperatorName()
        )));
    }
}
