package com.jzqs.app.dispatch.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/admin/dispatch")
public class DispatchController {
    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/board")
    public ApiResponse<PageResponse<DispatchBoardItemResponse>> board() {
        return ApiResponse.success(dispatchService.board());
    }

    @GetMapping("/overview")
    public ApiResponse<DispatchOverviewResponse> overview(
        @RequestParam String mealPeriod,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(dispatchService.overview(mealPeriod, serveDate));
    }

    @GetMapping("/batches")
    public ApiResponse<List<DispatchBatchResponse>> batches(
        @RequestParam(required = false) String serveDate,
        @RequestParam(required = false) String mealPeriod
    ) {
        return ApiResponse.success(dispatchService.batches(serveDate, mealPeriod));
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<DispatchExceptionItemResponse>> exceptions() {
        return ApiResponse.success(dispatchService.exceptions());
    }

    @GetMapping("/pending-items")
    public ApiResponse<List<DispatchPendingItemResponse>> pendingItems(
        @RequestParam String mealPeriod,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(dispatchService.pendingItems(mealPeriod, serveDate));
    }

    @PostMapping("/pending-items/batch-assign")
    @RateLimit(key = "admin:dispatch:batch-assign", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:batch-assign", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "BATCH_ASSIGN")
    public ApiResponse<BatchOperationResponse> batchAssignPendingOrders(@Valid @RequestBody DispatchBatchAssignRequest request) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        BatchOperationResponse response = dispatchService.batchAssignPendingOrders(
            request.orderIds(),
            request.areaCode(),
            operatorName
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/pending-riders")
    public ApiResponse<List<PendingRiderResponse>> pendingRiders() {
        return ApiResponse.success(dispatchService.pendingRiders());
    }

    @GetMapping("/riders")
    public ApiResponse<List<DispatchManagedRiderResponse>> managedRiders(
        @RequestParam(required = false) String authStatus,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String areaCode
    ) {
        return ApiResponse.success(dispatchService.managedRiders(authStatus, keyword, areaCode));
    }

    @GetMapping("/riders/progress")
    public ApiResponse<List<DispatchRiderProgressResponse>> riderProgress(
        @RequestParam(required = false) String mealPeriod,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(dispatchService.riderProgress(mealPeriod, serveDate));
    }

    @PostMapping("/riders")
    @RateLimit(key = "admin:dispatch:create-rider", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:create-rider", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "CREATE_RIDER")
    public ApiResponse<DispatchRiderProfileUpsertResponse> createRider(@Valid @RequestBody DispatchCreateRiderRequest request) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.createRider(
            request.riderName(),
            request.displayName(),
            request.phone(),
            request.areaCode(),
            request.employmentStatus(),
            operatorName
        ));
    }

    @PostMapping("/riders/{riderId}/profile")
    @RateLimit(key = "admin:dispatch:update-rider-profile", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:update-rider-profile", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "UPDATE_RIDER_PROFILE")
    public ApiResponse<DispatchRiderProfileUpsertResponse> updateRiderProfile(
        @PathVariable long riderId,
        @Valid @RequestBody DispatchRiderUpdateRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.updateRiderProfile(
            riderId,
            request.riderName(),
            request.displayName(),
            request.phone(),
            request.areaCode(),
            operatorName
        ));
    }

    @GetMapping("/riders/{riderId}/auth-binding")
    public ApiResponse<DispatchRiderAuthBindingResponse> riderAuthBinding(@PathVariable long riderId) {
        return ApiResponse.success(dispatchService.riderAuthBinding(riderId));
    }

    @PostMapping("/riders/{riderId}/takeover-auth")
    @RateLimit(key = "admin:dispatch:takeover-auth", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:takeover-auth", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "TAKEOVER_RIDER_AUTH")
    public ApiResponse<DispatchRiderAuthTakeoverResponse> takeoverRiderAuth(
        @PathVariable long riderId,
        @Valid @RequestBody DispatchTakeoverAuthRequest request
    ) {
        return ApiResponse.success(dispatchService.takeoverRiderAuth(
            riderId,
            request.sourceRiderId(),
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/riders/{riderId}/unbind-auth")
    @RateLimit(key = "admin:dispatch:unbind-auth", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:unbind-auth", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "DISPATCH", action = "UNBIND_RIDER_AUTH")
    public ApiResponse<DispatchRiderAuthUnbindResponse> unbindRiderAuth(@PathVariable long riderId) {
        return ApiResponse.success(dispatchService.unbindRiderAuth(riderId, AdminRequestContextSupport.requireOperatorName()));
    }

    @GetMapping("/area-bindings")
    public ApiResponse<List<DispatchAreaBindingResponse>> areaBindings(
        @RequestParam(required = false) String mealPeriod,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(dispatchService.areaBindings(mealPeriod, serveDate));
    }

    @PostMapping("/area-bindings/{areaCode}")
    @RateLimit(key = "admin:dispatch:update-area-binding", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:update-area-binding", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "UPDATE_AREA_BINDING")
    public ApiResponse<DispatchAreaBindingUpdateResultResponse> updateAreaBinding(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaBindingUpdateRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.updateAreaBinding(
            areaCode,
            request.keywords(),
            request.defaultRiderId(),
            request.backupRiderId(),
            operatorName
        ));
    }

    @PostMapping("/area-bindings/remove-rider")
    public ApiResponse<DispatchAreaBindingRemoveResponse> removeAreaBinding(
        @Valid @RequestBody DispatchAreaBindingRemoveRequest request
    ) {
        return ApiResponse.success(dispatchService.removeAreaBinding(request.areaCode(), request.riderId()));
    }

    @PutMapping("/area-bindings/{areaCode}/rename")
    public ApiResponse<DispatchAreaRenameResponse> renameArea(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaRenameRequest request
    ) {
        return ApiResponse.success(dispatchService.renameArea(areaCode, request.newAreaCode()));
    }

    @PostMapping("/area-bindings/delete")
    public ApiResponse<DispatchAreaDeleteResponse> deleteArea(@Valid @RequestBody DispatchAreaDeleteRequest request) {
        return ApiResponse.success(dispatchService.deleteArea(request.areaCode()));
    }

    @GetMapping("/reassignments")
    public ApiResponse<List<DispatchReassignmentResponse>> recentReassignments(@RequestParam(required = false) String serveDate) {
        return ApiResponse.success(dispatchService.recentReassignments(serveDate));
    }

    @PostMapping("/areas/{areaCode}/assign-rider")
    @RateLimit(key = "admin:dispatch:assign-rider-area", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:assign-rider-area", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "ASSIGN_RIDER_AREA")
    public ApiResponse<DispatchAreaRiderAssignResponse> assignRiderToArea(
        @PathVariable String areaCode,
        @RequestParam String mealPeriod,
        @Valid @RequestBody DispatchAreaRiderAssignRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        DispatchAreaRiderAssignResponse response = dispatchService.assignRiderToArea(
            areaCode,
            request.riderName(),
            operatorName,
            mealPeriod
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/areas/{areaCode}/orders/{orderId}/assign-rider")
    @RateLimit(key = "admin:dispatch:assign-rider-order", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:assign-rider-order", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "ASSIGN_RIDER_ORDER")
    public ApiResponse<DispatchAreaOrderAssignResponse> assignRiderToAreaOrder(
        @PathVariable String areaCode,
        @PathVariable long orderId,
        @Valid @RequestBody DispatchAreaRiderAssignRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        DispatchAreaOrderAssignResponse response = dispatchService.assignRiderToAreaOrder(
            areaCode,
            orderId,
            request.riderName(),
            operatorName
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/areas/{areaCode}/reorder")
    @Idempotent(key = "admin:dispatch:reorder-area", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "REORDER_AREA")
    public ApiResponse<DispatchAreaOrdersReorderResponse> reorderAreaOrders(
        @PathVariable String areaCode,
        @Valid @RequestBody List<DispatchOrderReorderItemRequest> items
    ) {
        DispatchAreaOrdersReorderResponse response = dispatchService.reorderAreaOrders(areaCode, items);
        return ApiResponse.success(response);
    }

    @PostMapping("/areas/{areaCode}/ai-correction/preview")
    @AuditAction(module = "DISPATCH", action = "AREA_AI_CORRECTION_PREVIEW")
    public ApiResponse<DispatchAreaAiCorrectionPreviewResponse> previewAreaAiCorrection(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaAiCorrectionPreviewRequest request
    ) {
        return ApiResponse.success(dispatchService.previewAreaAiCorrection(
            areaCode,
            request,
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/areas/{areaCode}/ai-correction/confirm")
    @AuditAction(module = "DISPATCH", action = "AREA_AI_CORRECTION_CONFIRM")
    public ApiResponse<DispatchAreaAiCorrectionPreviewResponse> confirmAreaAiCorrection(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaAiCorrectionConfirmRequest request
    ) {
        return ApiResponse.success(dispatchService.confirmAreaAiCorrection(
            areaCode,
            request,
            AdminRequestContextSupport.requireOperatorName()
        ));
    }

    @PostMapping("/areas/{areaCode}/orders/{orderId}/move")
    @RateLimit(key = "admin:dispatch:move-order-area", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:move-order-area", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "MOVE_ORDER_AREA")
    public ApiResponse<DispatchOrderAreaMoveResponse> moveOrderToArea(
        @PathVariable String areaCode,
        @PathVariable long orderId,
        @Valid @RequestBody DispatchOrderMoveRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        DispatchOrderAreaMoveResponse response = dispatchService.moveOrderToArea(
            areaCode,
            orderId,
            request.targetAreaCode(),
            operatorName
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/reassign")
    @RateLimit(key = "admin:dispatch:reassign", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:reassign", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "REASSIGN")
    public ApiResponse<DispatchReassignResultResponse> reassignDispatch(@Valid @RequestBody DispatchReassignRequest request) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        DispatchReassignResultResponse response = dispatchService.reassignDispatch(
            request.reassignLevel(),
            request.targetId(),
            request.fromRiderName(),
            request.toRiderName(),
            request.toAreaCode(),
            request.serveDate(),
            request.mealPeriod(),
            Boolean.TRUE.equals(request.syncDefaultBinding()),
            request.reason(),
            operatorName
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/auto-assign")
    public ApiResponse<DispatchAutoAssignResponse> autoAssign() {
        return ApiResponse.success(dispatchService.autoAssignPendingOrders());
    }

    @PostMapping("/exceptions/{mealSlotOrderId}/resolve")
    public ApiResponse<DispatchOrderAssignResponse> resolveException(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody DispatchExceptionResolveRequest request
    ) {
        DispatchOrderAssignResponse response = dispatchService.assignOrder(
            mealSlotOrderId,
            request.riderName(),
            request.areaCode()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/exceptions/{mealSlotOrderId}/confirm-area")
    @RateLimit(key = "admin:dispatch:confirm-exception-area", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:confirm-exception-area", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "CONFIRM_EXCEPTION_AREA")
    public ApiResponse<DispatchExceptionAreaConfirmResponse> confirmExceptionArea(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody DispatchExceptionConfirmRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        DispatchExceptionAreaConfirmResponse response = dispatchService.confirmExceptionArea(
            mealSlotOrderId,
            request.areaCode(),
            request.riderName(),
            Boolean.TRUE.equals(request.rememberAddress()),
            operatorName
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/{dispatchId}/notify")
    public ApiResponse<DispatchNotificationResponse> notifyCustomer(@PathVariable long dispatchId) {
        return ApiResponse.success(dispatchService.notifyCustomer(dispatchId));
    }

    @PostMapping("/assign")
    public ApiResponse<DispatchOrderAssignResponse> assign(@Valid @RequestBody DispatchAssignRequest request) {
        DispatchOrderAssignResponse response = dispatchService.assignOrder(
            request.mealSlotOrderId(),
            request.riderName(),
            request.areaCode()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/riders/{riderId}/activate")
    @RateLimit(key = "admin:dispatch:activate-rider", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:activate-rider", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "ACTIVATE_RIDER")
    public ApiResponse<DispatchRiderActivateResponse> activateRider(
        @PathVariable long riderId,
        @Valid @RequestBody ActivateRiderRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.activateRider(
            riderId,
            request.riderName(),
            request.areaCode(),
            operatorName
        ));
    }

    @PostMapping("/riders/{riderId}/disable")
    @RateLimit(key = "admin:dispatch:disable-rider", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:disable-rider", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "DISPATCH", action = "DISABLE_RIDER")
    public ApiResponse<DispatchRiderStatusResponse> disableRider(@PathVariable long riderId) {
        return ApiResponse.success(dispatchService.disableRider(riderId, AdminRequestContextSupport.requireOperatorName()));
    }

    @PostMapping("/areas/{areaCode}/route-suggestion")
    @RateLimit(key = "admin:dispatch:route-suggestion", maxRequests = 8, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:route-suggestion", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "GENERATE_ROUTE_SUGGESTION")
    public ApiResponse<DispatchRouteSuggestionResponse> suggestAreaRoute(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchRouteSuggestionRequest request
    ) {
        return ApiResponse.success(dispatchService.suggestAreaRoute(areaCode, request));
    }

    @PostMapping("/route-lab/simulate")
    @RateLimit(key = "admin:dispatch:route-lab", maxRequests = 10, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:route-lab", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "ROUTE_LAB_SIMULATE")
    public ApiResponse<DispatchRouteLabStartResponse> simulateRouteLab(
        @Valid @RequestBody DispatchRouteLabSimulateRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.startRouteLabSimulation(request, operatorName));
    }

    @GetMapping("/job-logs/{logId}")
    public ApiResponse<DispatchAiJobLogResponse> getDispatchAiJobLog(@PathVariable long logId) {
        return ApiResponse.success(dispatchService.getDispatchAiJobLog(logId));
    }

    @DeleteMapping("/job-logs")
    @AuditAction(module = "DISPATCH", action = "DELETE_JOB_LOGS")
    public ApiResponse<Void> deleteJobLogs(@Valid @RequestBody DispatchDeleteJobLogsRequest request) {
        dispatchService.deleteJobLogs(request.ids());
        return ApiResponse.success(null);
    }

    @PostMapping("/areas/{areaCode}/route-suggestion-feedback")
    @RateLimit(key = "admin:dispatch:route-feedback", maxRequests = 8, windowSeconds = 10)
    @Idempotent(key = "admin:dispatch:route-feedback", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "DISPATCH", action = "ROUTE_FEEDBACK")
    public ApiResponse<DispatchRouteSuggestionFeedbackResponse> saveRouteSuggestionFeedback(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchRouteSuggestionFeedbackRequest request
    ) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(dispatchService.saveRouteSuggestionFeedback(areaCode, request, operatorName));
    }
}
