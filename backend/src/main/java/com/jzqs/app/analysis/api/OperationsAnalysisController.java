package com.jzqs.app.analysis.api;

import com.jzqs.app.analysis.service.OperationsAnalysisService;
import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analysis")
public class OperationsAnalysisController {
    private final OperationsAnalysisService operationsAnalysisService;

    public OperationsAnalysisController(OperationsAnalysisService operationsAnalysisService) {
        this.operationsAnalysisService = operationsAnalysisService;
    }

    @GetMapping("/overview")
    public ApiResponse<AnalysisOverviewResponse> overview(@RequestParam(required = false) String date) {
        return ApiResponse.success(operationsAnalysisService.overview(date));
    }

    @GetMapping("/cost-entries")
    public ApiResponse<List<CostEntryItem>> costEntries(@RequestParam(required = false) String month) {
        return ApiResponse.success(operationsAnalysisService.costEntries(month));
    }

    @PostMapping("/cost-entries")
    public ApiResponse<CreateCostEntryResponse> createCostEntry(@Valid @RequestBody CreateCostEntryRequest request) {
        return ApiResponse.success(operationsAnalysisService.createCostEntry(request));
    }
}
