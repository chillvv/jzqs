package com.jzqs.app.audit.api;

import com.jzqs.app.audit.service.AdminOperationLogService;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class AdminOperationLogController {
    private final AdminOperationLogService adminOperationLogService;

    public AdminOperationLogController(AdminOperationLogService adminOperationLogService) {
        this.adminOperationLogService = adminOperationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOperationLogResponse>> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) Long operatorId,
        @RequestParam(required = false) String operatorName,
        @RequestParam(required = false) String module,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        return ApiResponse.success(adminOperationLogService.list(
            page, pageSize, operatorId, operatorName, module, status, startDate, endDate
        ));
    }
}
