package com.jzqs.app.aftersale.api;

import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody AdminAftersaleCreateRequest body) {
        return ApiResponse.success(aftersaleService.createCase(body));
    }

    @PostMapping("/{caseId}/resolve")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable long caseId, @Valid @RequestBody AdminAftersaleResolveRequest body) {
        return ApiResponse.success(aftersaleService.resolveCase(caseId, body));
    }
}
