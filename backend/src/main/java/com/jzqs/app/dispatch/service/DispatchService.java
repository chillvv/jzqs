package com.jzqs.app.dispatch.service;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingRemoveResponse;
import com.jzqs.app.dispatch.api.DispatchBatchResponse;
import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionConfirmRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchAreaDeleteResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrdersReorderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRenameResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRiderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAutoAssignResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionAreaConfirmResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionItemResponse;
import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchNotificationResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAreaMoveResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchReassignResultResponse;
import com.jzqs.app.dispatch.api.DispatchReassignmentResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthTakeoverResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthUnbindResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingUpdateResultResponse;
import com.jzqs.app.dispatch.api.DispatchRiderActivateResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import com.jzqs.app.dispatch.api.DispatchRiderStatusResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProgressResponse;
import com.jzqs.app.dispatch.api.DispatchRouteLabStartResponse;
import com.jzqs.app.dispatch.api.DispatchRouteLabSimulateRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import java.util.List;

public interface DispatchService {
    DispatchAreaAiCorrectionPreviewResponse previewAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionPreviewRequest request,
        String operatorName
    );

    DispatchAreaAiCorrectionPreviewResponse confirmAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionConfirmRequest request,
        String operatorName
    );

    DispatchRouteSuggestionResponse suggestAreaRoute(String areaCode, DispatchRouteSuggestionRequest request);

    DispatchRouteSuggestionResponse simulateRouteLab(DispatchRouteLabSimulateRequest request, String operatorName);

    DispatchRouteLabStartResponse startRouteLabSimulation(DispatchRouteLabSimulateRequest request, String operatorName);

    com.jzqs.app.settings.api.DispatchAiJobLogResponse getDispatchAiJobLog(long logId);

    void deleteJobLogs(List<Long> ids);

    DispatchRouteSuggestionFeedbackResponse saveRouteSuggestionFeedback(String areaCode, DispatchRouteSuggestionFeedbackRequest request, String operatorName);
    void preRouteTomorrowAreas();
    PageResponse<DispatchBoardItemResponse> board();

    DispatchOverviewResponse overview(String mealPeriod, String serveDate);

    List<DispatchBatchResponse> batches(String serveDate, String mealPeriod);

    List<DispatchExceptionItemResponse> exceptions();

    List<DispatchPendingItemResponse> pendingItems(String mealPeriod, String serveDate);

    DispatchAutoAssignResponse autoAssignPendingOrders();

    DispatchAutoAssignResponse autoAssignPendingOrders(String mealPeriod);

    BatchOperationResponse batchAssignPendingOrders(List<Long> orderIds, String areaCode, String updatedBy);

    DispatchNotificationResponse notifyCustomer(long dispatchId);

    DispatchOrderAssignResponse assignOrder(long orderId, String riderName, String areaCode);

    DispatchExceptionAreaConfirmResponse confirmExceptionArea(
        long mealSlotOrderId,
        String areaCode,
        String riderName,
        boolean rememberAddress,
        String updatedBy
    );

    List<PendingRiderResponse> pendingRiders();

    List<DispatchManagedRiderResponse> managedRiders(String authStatus, String keyword, String areaCode);

    List<DispatchRiderProgressResponse> riderProgress(String mealPeriod, String serveDate);

    DispatchRiderProfileUpsertResponse createRider(String riderName, String displayName, String phone, String areaCode, String employmentStatus, String updatedBy);

    DispatchRiderProfileUpsertResponse updateRiderProfile(long riderId, String riderName, String displayName, String phone, String areaCode, String updatedBy);

    DispatchRiderAuthBindingResponse riderAuthBinding(long riderId);

    DispatchRiderAuthTakeoverResponse takeoverRiderAuth(long riderId, long sourceRiderId, String assignedBy);

    DispatchRiderAuthUnbindResponse unbindRiderAuth(long riderId, String assignedBy);

    List<DispatchAreaBindingResponse> areaBindings(String mealPeriod, String serveDate);

    DispatchAreaBindingUpdateResultResponse updateAreaBinding(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy);

    DispatchAreaBindingRemoveResponse removeAreaBinding(String areaCode, long riderId);

    DispatchAreaRenameResponse renameArea(String areaCode, String newAreaCode);

    DispatchAreaDeleteResponse deleteArea(String areaCode);

    List<DispatchReassignmentResponse> recentReassignments(String serveDate);

    DispatchAreaRiderAssignResponse assignRiderToArea(String areaCode, String riderName, String updatedBy, String mealPeriod);

    DispatchAreaOrderAssignResponse assignRiderToAreaOrder(String areaCode, long orderId, String riderName, String updatedBy);

    DispatchAreaOrdersReorderResponse reorderAreaOrders(String areaCode, List<com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest> items);

    DispatchOrderAreaMoveResponse moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy);

    DispatchReassignResultResponse reassignDispatch(
        String reassignLevel,
        long targetId,
        String fromRiderName,
        String toRiderName,
        String toAreaCode,
        String serveDate,
        String mealPeriod,
        boolean syncDefaultBinding,
        String reason,
        String createdBy
    );

    DispatchRiderActivateResponse activateRider(long riderId, String riderName, String areaCode, String assignedBy);

    DispatchRiderStatusResponse disableRider(long riderId, String assignedBy);
}
