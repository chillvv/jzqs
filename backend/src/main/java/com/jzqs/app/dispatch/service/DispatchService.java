package com.jzqs.app.dispatch.service;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.dispatch.api.DispatchBatchResponse;
import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionItemResponse;
import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchReassignmentResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import java.util.List;
import java.util.Map;

public interface DispatchService {
    PageResponse<DispatchBoardItemResponse> board();

    DispatchOverviewResponse overview(String mealPeriod, String serveDate);

    List<DispatchBatchResponse> batches(String serveDate, String mealPeriod);

    List<DispatchExceptionItemResponse> exceptions();

    List<DispatchPendingItemResponse> pendingItems(String mealPeriod, String serveDate);

    Map<String, Object> autoAssignPendingOrders();

    BatchOperationResponse batchAssignPendingOrders(List<Long> orderIds, String areaCode, String updatedBy);

    Map<String, Object> notifyCustomer(long dispatchId);

    Map<String, Object> assignOrder(long orderId, String riderName, String areaCode);

    Map<String, Object> confirmExceptionArea(long mealSlotOrderId, String areaCode, String riderName, boolean rememberAddress, String updatedBy);

    List<PendingRiderResponse> pendingRiders();

    List<DispatchManagedRiderResponse> managedRiders(String authStatus, String keyword, String areaCode);

    Map<String, Object> createRider(String riderName, String displayName, String phone, String areaCode, String employmentStatus, String updatedBy);

    Map<String, Object> updateRiderProfile(long riderId, String riderName, String displayName, String phone, String areaCode, String updatedBy);

    DispatchRiderAuthBindingResponse riderAuthBinding(long riderId);

    Map<String, Object> takeoverRiderAuth(long riderId, long sourceRiderId, String assignedBy);

    Map<String, Object> unbindRiderAuth(long riderId, String assignedBy);

    List<DispatchAreaBindingResponse> areaBindings(String mealPeriod, String serveDate);

    Map<String, Object> updateAreaBinding(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy);

    Map<String, Object> removeAreaBinding(String areaCode, long riderId);

    Map<String, Object> renameArea(String areaCode, String newAreaCode);

    Map<String, Object> deleteArea(String areaCode);

    List<DispatchReassignmentResponse> recentReassignments(String serveDate);

    Map<String, Object> assignRiderToArea(String areaCode, String riderName, String updatedBy, String mealPeriod);

    Map<String, Object> assignRiderToAreaOrder(String areaCode, long orderId, String riderName, String updatedBy);

    Map<String, Object> reorderAreaOrders(String areaCode, List<com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest> items);

    Map<String, Object> moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy);

    Map<String, Object> reassignDispatch(
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

    Map<String, Object> activateRider(long riderId, String riderName, String areaCode, String assignedBy);

    Map<String, Object> disableRider(long riderId, String assignedBy);
}
