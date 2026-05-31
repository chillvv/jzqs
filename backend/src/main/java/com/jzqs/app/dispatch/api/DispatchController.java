package com.jzqs.app.dispatch.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<BatchOperationResponse> batchAssignPendingOrders(@Valid @RequestBody DispatchBatchAssignRequest request) {
        return ApiResponse.success(dispatchService.batchAssignPendingOrders(
            request.orderIds(),
            request.areaCode(),
            request.updatedBy()
        ));
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

    @PostMapping("/riders")
    public ApiResponse<Map<String, Object>> createRider(@Valid @RequestBody DispatchCreateRiderRequest request) {
        return ApiResponse.success(dispatchService.createRider(
            request.riderName(),
            request.displayName(),
            request.phone(),
            request.password(),
            request.areaCode(),
            request.employmentStatus(),
            request.updatedBy()
        ));
    }

    @PostMapping("/riders/{riderId}/profile")
    public ApiResponse<Map<String, Object>> updateRiderProfile(
        @PathVariable long riderId,
        @Valid @RequestBody DispatchRiderUpdateRequest request
    ) {
        return ApiResponse.success(dispatchService.updateRiderProfile(
            riderId,
            request.riderName(),
            request.displayName(),
            request.phone(),
            request.password(),
            request.areaCode(),
            request.updatedBy()
        ));
    }

    @GetMapping("/riders/{riderId}/auth-binding")
    public ApiResponse<DispatchRiderAuthBindingResponse> riderAuthBinding(@PathVariable long riderId) {
        return ApiResponse.success(dispatchService.riderAuthBinding(riderId));
    }

    @PostMapping("/riders/{riderId}/takeover-auth")
    public ApiResponse<Map<String, Object>> takeoverRiderAuth(
        @PathVariable long riderId,
        @Valid @RequestBody DispatchTakeoverAuthRequest request
    ) {
        return ApiResponse.success(dispatchService.takeoverRiderAuth(riderId, request.sourceRiderId(), request.assignedBy()));
    }

    @PostMapping("/riders/{riderId}/unbind-auth")
    public ApiResponse<Map<String, Object>> unbindRiderAuth(
        @PathVariable long riderId,
        @RequestParam(defaultValue = "系统") String assignedBy
    ) {
        return ApiResponse.success(dispatchService.unbindRiderAuth(riderId, assignedBy));
    }

    @GetMapping("/area-bindings")
    public ApiResponse<List<DispatchAreaBindingResponse>> areaBindings(
        @RequestParam(required = false) String mealPeriod,
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(dispatchService.areaBindings(mealPeriod, serveDate));
    }

    @PostMapping("/area-bindings/{areaCode}")
    public ApiResponse<Map<String, Object>> updateAreaBinding(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaBindingUpdateRequest request
    ) {
        return ApiResponse.success(dispatchService.updateAreaBinding(
            areaCode,
            request.keywords(),
            request.defaultRiderId(),
            request.backupRiderId(),
            request.updatedBy()
        ));
    }

    @PostMapping("/area-bindings/remove-rider")
    public ApiResponse<Map<String, Object>> removeAreaBinding(
        @Valid @RequestBody DispatchAreaBindingRemoveRequest request
    ) {
        return ApiResponse.success(dispatchService.removeAreaBinding(request.areaCode(), request.riderId()));
    }

    @PutMapping("/area-bindings/{areaCode}/rename")
    public ApiResponse<Map<String, Object>> renameArea(
        @PathVariable String areaCode,
        @Valid @RequestBody DispatchAreaRenameRequest request
    ) {
        return ApiResponse.success(dispatchService.renameArea(areaCode, request.newAreaCode()));
    }

    @PostMapping("/area-bindings/delete")
    public ApiResponse<Map<String, Object>> deleteArea(@Valid @RequestBody DispatchAreaDeleteRequest request) {
        return ApiResponse.success(dispatchService.deleteArea(request.areaCode()));
    }

    @GetMapping("/reassignments")
    public ApiResponse<List<DispatchReassignmentResponse>> recentReassignments(@RequestParam(required = false) String serveDate) {
        return ApiResponse.success(dispatchService.recentReassignments(serveDate));
    }

    @PostMapping("/areas/{areaCode}/assign-rider")
    public ApiResponse<Map<String, Object>> assignRiderToArea(
        @PathVariable String areaCode,
        @RequestParam String mealPeriod,
        @Valid @RequestBody DispatchAreaRiderAssignRequest request
    ) {
        return ApiResponse.success(dispatchService.assignRiderToArea(
            areaCode,
            request.riderName(),
            request.updatedBy(),
            mealPeriod
        ));
    }

    @PostMapping("/areas/{areaCode}/orders/{orderId}/assign-rider")
    public ApiResponse<Map<String, Object>> assignRiderToAreaOrder(
        @PathVariable String areaCode,
        @PathVariable long orderId,
        @Valid @RequestBody DispatchAreaRiderAssignRequest request
    ) {
        return ApiResponse.success(dispatchService.assignRiderToAreaOrder(
            areaCode,
            orderId,
            request.riderName(),
            request.updatedBy()
        ));
    }

    @PostMapping("/areas/{areaCode}/reorder")
    public ApiResponse<Map<String, Object>> reorderAreaOrders(
        @PathVariable String areaCode,
        @Valid @RequestBody List<DispatchOrderReorderItemRequest> items
    ) {
        return ApiResponse.success(dispatchService.reorderAreaOrders(areaCode, items));
    }

    @PostMapping("/areas/{areaCode}/orders/{orderId}/move")
    public ApiResponse<Map<String, Object>> moveOrderToArea(
        @PathVariable String areaCode,
        @PathVariable long orderId,
        @Valid @RequestBody DispatchOrderMoveRequest request
    ) {
        return ApiResponse.success(dispatchService.moveOrderToArea(
            areaCode,
            orderId,
            request.targetAreaCode(),
            request.updatedBy()
        ));
    }

    @PostMapping("/reassign")
    public ApiResponse<Map<String, Object>> reassignDispatch(@Valid @RequestBody DispatchReassignRequest request) {
        return ApiResponse.success(dispatchService.reassignDispatch(
            request.reassignLevel(),
            request.targetId(),
            request.fromRiderName(),
            request.toRiderName(),
            request.toAreaCode(),
            request.serveDate(),
            request.mealPeriod(),
            Boolean.TRUE.equals(request.syncDefaultBinding()),
            request.reason(),
            request.createdBy()
        ));
    }

    @PostMapping("/auto-assign")
    public ApiResponse<Map<String, Object>> autoAssign() {
        return ApiResponse.success(dispatchService.autoAssignPendingOrders());
    }

    @PostMapping("/exceptions/{mealSlotOrderId}/resolve")
    public ApiResponse<Map<String, Object>> resolveException(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody DispatchExceptionResolveRequest request
    ) {
        return ApiResponse.success(dispatchService.assignOrder(
            mealSlotOrderId,
            request.riderName(),
            request.areaCode()
        ));
    }

    @PostMapping("/exceptions/{mealSlotOrderId}/confirm-area")
    public ApiResponse<Map<String, Object>> confirmExceptionArea(
        @PathVariable long mealSlotOrderId,
        @Valid @RequestBody DispatchExceptionConfirmRequest request
    ) {
        return ApiResponse.success(dispatchService.confirmExceptionArea(
            mealSlotOrderId,
            request.areaCode(),
            request.riderName(),
            Boolean.TRUE.equals(request.rememberAddress()),
            request.updatedBy()
        ));
    }

    @PostMapping("/{dispatchId}/notify")
    public ApiResponse<Map<String, Object>> notifyCustomer(@PathVariable long dispatchId) {
        return ApiResponse.success(dispatchService.notifyCustomer(dispatchId));
    }

    @PostMapping("/assign")
    public ApiResponse<Map<String, Object>> assign(@Valid @RequestBody DispatchAssignRequest request) {
        return ApiResponse.success(dispatchService.assignOrder(
            request.mealSlotOrderId(),
            request.riderName(),
            request.areaCode()
        ));
    }

    @PostMapping("/riders/{riderId}/activate")
    public ApiResponse<Map<String, Object>> activateRider(
        @PathVariable long riderId,
        @Valid @RequestBody ActivateRiderRequest request
    ) {
        return ApiResponse.success(dispatchService.activateRider(
            riderId,
            request.riderName(),
            request.areaCode(),
            request.assignedBy()
        ));
    }

    @PostMapping("/riders/{riderId}/disable")
    public ApiResponse<Map<String, Object>> disableRider(
        @PathVariable long riderId,
        @RequestParam(defaultValue = "系统") String assignedBy
    ) {
        return ApiResponse.success(dispatchService.disableRider(riderId, assignedBy));
    }
}
