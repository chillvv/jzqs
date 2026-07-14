package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.mobile.MobileAuthService;
import com.jzqs.app.mobile.MobilePortalService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mobile/rider")
public class MobileRiderController {
    private final MobileAuthService mobileAuthService;
    private final MobilePortalService mobilePortalService;

    public MobileRiderController(MobileAuthService mobileAuthService, MobilePortalService mobilePortalService) {
        this.mobileAuthService = mobileAuthService;
        this.mobilePortalService = mobilePortalService;
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<RiderTaskItemResponse>> tasks(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId
    ) {
        return ApiResponse.success(mobilePortalService.riderTasks(resolveCurrentRiderName(riderName, riderId)));
    }

    @GetMapping("/summary")
    public ApiResponse<RiderBatchSummaryResponse> summary(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(mobilePortalService.riderSummary(resolveCurrentRiderName(riderName, riderId), serveDate));
    }

    @GetMapping("/queue")
    public ApiResponse<PageResponse<RiderQueueItemResponse>> queue(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(mobilePortalService.riderQueue(resolveCurrentRiderName(riderName, riderId), serveDate));
    }

    @PostMapping(value = "/uploads/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RiderDeliveryUploadResponse> uploadReceipt(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(mobilePortalService.uploadRiderReceipt(resolveCurrentRiderName(riderName, riderId), file));
    }

    @PostMapping("/tasks/{mealSlotOrderId}/receipt")
    public ApiResponse<DeliveryReceiptRecordResponse> submitReceipt(
        @PathVariable long mealSlotOrderId,
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @Valid @RequestBody RiderReceiptRequest request
    ) {
        DeliveryReceiptRecordResponse response = mobilePortalService.submitRiderReceipt(
            mealSlotOrderId,
            resolveCurrentRiderName(riderName, riderId),
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/tasks/{mealSlotOrderId}/receipt/update")
    public ApiResponse<DeliveryReceiptRecordResponse> updateReceipt(
        @PathVariable long mealSlotOrderId,
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @Valid @RequestBody RiderReceiptRequest request
    ) {
        DeliveryReceiptRecordResponse response = mobilePortalService.updateRiderReceipt(
            mealSlotOrderId,
            resolveCurrentRiderName(riderName, riderId),
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/reorder")
    public ApiResponse<RiderQueueReorderResponse> reorderQueue(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @Valid @RequestBody RiderReorderRequest request
    ) {
        RiderQueueReorderResponse response = mobilePortalService.reorderRiderQueue(
            resolveCurrentRiderName(riderName, riderId),
            request.batchItemIds()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/items/{batchItemId}/defer")
    public ApiResponse<RiderQueueItemActionResponse> deferQueueItem(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @PathVariable long batchItemId
    ) {
        RiderQueueItemActionResponse response = mobilePortalService.deferRiderQueueItem(
            resolveCurrentRiderName(riderName, riderId),
            batchItemId
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/queue/items/{batchItemId}/resume")
    public ApiResponse<RiderQueueItemActionResponse> resumeQueueItem(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @PathVariable long batchItemId
    ) {
        RiderQueueItemActionResponse response = mobilePortalService.resumeRiderQueueItem(
            resolveCurrentRiderName(riderName, riderId),
            batchItemId
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/tasks/{mealSlotOrderId}/report-exception")
    public ApiResponse<RiderDeliveryExceptionReportResponse> reportException(
        @PathVariable long mealSlotOrderId,
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId,
        @Valid @RequestBody RiderExceptionReportRequest request
    ) {
        RiderDeliveryExceptionReportResponse response = mobilePortalService.reportDeliveryException(
            mealSlotOrderId,
            resolveCurrentRiderName(riderName, riderId),
            request.exceptionType(),
            request.exceptionNote(),
            request.exceptionImages()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/completed-today")
    public ApiResponse<PageResponse<RiderTaskItemResponse>> completedToday(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId
    ) {
        return ApiResponse.success(mobilePortalService.riderCompletedToday(resolveCurrentRiderName(riderName, riderId)));
    }

    private String resolveCurrentRiderName(String riderName, Long riderId) {
        if (riderName != null && !riderName.isBlank()) {
            return riderName;
        }
        if (riderId == null) {
            throw new IllegalArgumentException("缺少骑手认证信息");
        }
        return mobileAuthService.riderProfile(riderId).riderName();
    }
}
