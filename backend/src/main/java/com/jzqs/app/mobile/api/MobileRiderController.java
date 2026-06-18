package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.mobile.MobilePortalService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mobile/rider")
public class MobileRiderController {
    private final MobilePortalService mobilePortalService;

    public MobileRiderController(MobilePortalService mobilePortalService) {
        this.mobilePortalService = mobilePortalService;
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<RiderTaskItemResponse>> tasks(@RequestParam String riderName) {
        return ApiResponse.success(mobilePortalService.riderTasks(riderName));
    }

    @GetMapping("/summary")
    public ApiResponse<RiderBatchSummaryResponse> summary(@RequestParam String riderName, @RequestParam(required = false) String serveDate) {
        return ApiResponse.success(mobilePortalService.riderSummary(riderName, serveDate));
    }

    @GetMapping("/queue")
    public ApiResponse<PageResponse<RiderQueueItemResponse>> queue(@RequestParam String riderName, @RequestParam(required = false) String serveDate) {
        return ApiResponse.success(mobilePortalService.riderQueue(riderName, serveDate));
    }

    @PostMapping(value = "/uploads/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RiderDeliveryUploadResponse> uploadReceipt(
        @RequestParam String riderName,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(mobilePortalService.uploadRiderReceipt(riderName, file));
    }

    @PostMapping("/tasks/{mealSlotOrderId}/receipt")
    public ApiResponse<Map<String, Object>> submitReceipt(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody RiderReceiptRequest request
    ) {
        Map<String, Object> response = mobilePortalService.submitRiderReceipt(
            mealSlotOrderId,
            request.riderName(),
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/tasks/{mealSlotOrderId}/receipt/update")
    public ApiResponse<Map<String, Object>> updateReceipt(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody RiderReceiptRequest request
    ) {
        Map<String, Object> response = mobilePortalService.updateRiderReceipt(
            mealSlotOrderId,
            request.riderName(),
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/reorder")
    public ApiResponse<Map<String, Object>> reorderQueue(
        @RequestParam String riderName,
        @Valid @RequestBody RiderReorderRequest request
    ) {
        Map<String, Object> response = mobilePortalService.reorderRiderQueue(riderName, request.batchItemIds());
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/items/{batchItemId}/defer")
    public ApiResponse<Map<String, Object>> deferQueueItem(
        @RequestParam String riderName,
        @PathVariable long batchItemId
    ) {
        Map<String, Object> response = mobilePortalService.deferRiderQueueItem(riderName, batchItemId);
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/items/{batchItemId}/resume")
    public ApiResponse<Map<String, Object>> resumeQueueItem(
        @RequestParam String riderName,
        @PathVariable long batchItemId
    ) {
        Map<String, Object> response = mobilePortalService.resumeRiderQueueItem(riderName, batchItemId);
        return ApiResponse.success(response);
    }

    @PostMapping("/tasks/{mealSlotOrderId}/report-exception")
    public ApiResponse<Map<String, Object>> reportException(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody RiderExceptionReportRequest request
    ) {
        Map<String, Object> response = mobilePortalService.reportDeliveryException(
            mealSlotOrderId,
            request.riderName(),
            request.exceptionType(),
            request.exceptionNote(),
            request.exceptionImages()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/completed-today")
    public ApiResponse<PageResponse<RiderTaskItemResponse>> completedToday(@RequestParam String riderName) {
        return ApiResponse.success(mobilePortalService.riderCompletedToday(riderName));
    }
}
