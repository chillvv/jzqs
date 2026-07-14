package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.admin.persistence.AdminRowMappers;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionConfirmRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingRemoveResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingUpdateResultResponse;
import com.jzqs.app.dispatch.api.DispatchAreaDeleteResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAutoAssignResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrdersReorderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRenameResponse;
import com.jzqs.app.dispatch.api.DispatchAreaRiderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchBatchResponse;
import com.jzqs.app.dispatch.api.DispatchBoardItemResponse;
import com.jzqs.app.dispatch.api.DispatchAreaBindingResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionAreaConfirmResponse;
import com.jzqs.app.dispatch.api.DispatchExceptionItemResponse;
import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchNotificationResponse;
import com.jzqs.app.dispatch.api.DispatchOverviewResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAreaMoveResponse;
import com.jzqs.app.dispatch.api.DispatchOrderAssignResponse;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import com.jzqs.app.dispatch.api.DispatchReassignResultResponse;
import com.jzqs.app.dispatch.api.DispatchReassignmentResponse;
import com.jzqs.app.dispatch.api.DispatchRiderActivateResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthTakeoverResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthUnbindResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProgressResponse;
import com.jzqs.app.dispatch.api.DispatchRiderStatusResponse;
import com.jzqs.app.dispatch.api.DispatchRouteLabStartResponse;
import com.jzqs.app.dispatch.api.DispatchRouteLabSimulateRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionItemResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import com.jzqs.app.dispatch.service.route.DispatchRouteScoringService;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchServiceImpl implements DispatchService {
    private static final String DEFAULT_OPERATOR = "system";
    private static final int JOB_LOG_MESSAGE_MAX_LENGTH = 255;
    private static final int JOB_LOG_REASON_MAX_LENGTH = 255;
    private static final int JOB_LOG_SOURCE_MAX_LENGTH = 32;
    private final JdbcTemplate jdbcTemplate;
    private final DispatchRouteScoringService dispatchRouteScoringService;
    private final DispatchBatchModule dispatchBatchModule;
    private final DispatchRiderAdminModule dispatchRiderAdminModule;
    private final DispatchAreaAdminModule dispatchAreaAdminModule;
    private final DispatchAssignmentModule dispatchAssignmentModule;
    private final DispatchNotificationModule dispatchNotificationModule;
    private final DispatchQueryModule dispatchQueryModule;
    private final DispatchRouteLabModule dispatchRouteLabModule;
    private final DispatchRouteSuggestionModule dispatchRouteSuggestionModule;
    private final DispatchAreaCorrectionModule dispatchAreaCorrectionModule;

    @Autowired
    public DispatchServiceImpl(
        JdbcTemplate jdbcTemplate,
        DispatchRouteScoringService dispatchRouteScoringService,
        DispatchBatchModule dispatchBatchModule,
        DispatchRiderAdminModule dispatchRiderAdminModule,
        DispatchAreaAdminModule dispatchAreaAdminModule,
        DispatchAssignmentModule dispatchAssignmentModule,
        DispatchNotificationModule dispatchNotificationModule,
        DispatchQueryModule dispatchQueryModule,
        DispatchRouteLabModule dispatchRouteLabModule,
        DispatchRouteSuggestionModule dispatchRouteSuggestionModule,
        DispatchAreaCorrectionModule dispatchAreaCorrectionModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchRouteScoringService = dispatchRouteScoringService;
        this.dispatchBatchModule = dispatchBatchModule;
        this.dispatchRiderAdminModule = dispatchRiderAdminModule;
        this.dispatchAreaAdminModule = dispatchAreaAdminModule;
        this.dispatchAssignmentModule = dispatchAssignmentModule;
        this.dispatchNotificationModule = dispatchNotificationModule;
        this.dispatchQueryModule = dispatchQueryModule;
        this.dispatchRouteLabModule = dispatchRouteLabModule;
        this.dispatchRouteSuggestionModule = dispatchRouteSuggestionModule;
        this.dispatchAreaCorrectionModule = dispatchAreaCorrectionModule;
    }

    DispatchServiceImpl(
        JdbcTemplate jdbcTemplate,
        TransactionalRealtimePublisher realtimeEventPublisher
    ) {
        this(jdbcTemplate, DispatchLegacyWiring.create(jdbcTemplate, realtimeEventPublisher));
    }

    private DispatchServiceImpl(
        JdbcTemplate jdbcTemplate,
        DispatchLegacyWiring.Modules modules
    ) {
        this(
            jdbcTemplate,
            modules.dispatchRouteScoringService(),
            modules.dispatchBatchModule(),
            modules.dispatchRiderAdminModule(),
            modules.dispatchAreaAdminModule(),
            modules.dispatchAssignmentModule(),
            modules.dispatchNotificationModule(),
            modules.dispatchQueryModule(),
            modules.dispatchRouteLabModule(),
            modules.dispatchRouteSuggestionModule(),
            modules.dispatchAreaCorrectionModule()
        );
    }

    @Override
    public PageResponse<DispatchBoardItemResponse> board() {
        return dispatchQueryModule.board();
    }

    @Override
    public DispatchOverviewResponse overview(String mealPeriod, String serveDate) {
        return dispatchQueryModule.overview(mealPeriod, serveDate);
    }

    @Override
    public List<DispatchBatchResponse> batches(String serveDate, String mealPeriod) {
        return dispatchQueryModule.batches(serveDate, mealPeriod);
    }

    @Override
    public List<DispatchRiderProgressResponse> riderProgress(String mealPeriod, String serveDate) {
        return dispatchQueryModule.riderProgress(mealPeriod, serveDate);
    }

    @Override
    public List<DispatchExceptionItemResponse> exceptions() {
        return dispatchQueryModule.exceptions();
    }

    @Override
    public List<DispatchPendingItemResponse> pendingItems(String mealPeriod, String serveDate) {
        return dispatchQueryModule.pendingItems(mealPeriod, serveDate);
    }

    @Override
    @Transactional
    public DispatchAutoAssignResponse autoAssignPendingOrders() {
        return autoAssignPendingOrders(null);
    }

    @Override
    @Transactional
    public DispatchAutoAssignResponse autoAssignPendingOrders(String mealPeriod) {
        return dispatchAssignmentModule.autoAssignPendingOrders(mealPeriod);
    }

    @Override
    @Transactional
    public BatchOperationResponse batchAssignPendingOrders(
        List<Long> orderIds,
        String areaCode,
        String updatedBy
    ) {
        return dispatchAssignmentModule.batchAssignPendingOrders(orderIds, areaCode, updatedBy);
    }

    @Override
    @Transactional
    public DispatchNotificationResponse notifyCustomer(long dispatchId) {
        return dispatchNotificationModule.notifyCustomer(dispatchId);
    }

    @Override
    @Transactional
    public DispatchOrderAssignResponse assignOrder(long orderId, String riderName, String areaCode) {
        return dispatchAssignmentModule.assignOrder(orderId, riderName, areaCode);
    }

    @Override
    @Transactional
    public DispatchExceptionAreaConfirmResponse confirmExceptionArea(
        long mealSlotOrderId,
        String areaCode,
        String riderName,
        boolean rememberAddress,
        String updatedBy
    ) {
        return dispatchAssignmentModule.confirmExceptionArea(mealSlotOrderId, areaCode, riderName, rememberAddress, updatedBy);
    }

    @Override
    public List<PendingRiderResponse> pendingRiders() {
        return dispatchRiderAdminModule.pendingRiders();
    }

    @Override
    public List<DispatchManagedRiderResponse> managedRiders(String authStatus, String keyword, String areaCode) {
        return dispatchRiderAdminModule.managedRiders(authStatus, keyword, areaCode);
    }

    @Override
    @Transactional
    public DispatchRiderProfileUpsertResponse createRider(
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String employmentStatus,
        String updatedBy
    ) {
        return dispatchRiderAdminModule.createRider(
            riderName,
            displayName,
            phone,
            areaCode,
            employmentStatus,
            updatedBy,
            (bindingAreaCode, keywords, defaultRiderId, backupRiderId, operator) ->
                updateAreaBinding(bindingAreaCode, keywords, defaultRiderId, backupRiderId, operator)
        );
    }

    @Override
    @Transactional
    public DispatchRiderProfileUpsertResponse updateRiderProfile(
        long riderId,
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String updatedBy
    ) {
        return dispatchRiderAdminModule.updateRiderProfile(riderId, riderName, displayName, phone, areaCode, updatedBy);
    }

    @Override
    public DispatchRiderAuthBindingResponse riderAuthBinding(long riderId) {
        return dispatchRiderAdminModule.riderAuthBinding(riderId);
    }

    @Override
    @Transactional
    public DispatchRiderAuthTakeoverResponse takeoverRiderAuth(long riderId, long sourceRiderId, String assignedBy) {
        return dispatchRiderAdminModule.takeoverRiderAuth(riderId, sourceRiderId, assignedBy);
    }

    @Override
    @Transactional
    public DispatchRiderAuthUnbindResponse unbindRiderAuth(long riderId, String assignedBy) {
        return dispatchRiderAdminModule.unbindRiderAuth(riderId, assignedBy);
    }

    @Override
    public List<DispatchAreaBindingResponse> areaBindings(String mealPeriod, String serveDate) {
        return dispatchQueryModule.areaBindings(mealPeriod, serveDate);
    }

    @Override
    @Transactional
    public DispatchAreaBindingUpdateResultResponse updateAreaBinding(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy) {
        return dispatchAreaAdminModule.updateAreaBinding(areaCode, keywords, defaultRiderId, backupRiderId, updatedBy);
    }

    @Override
    public DispatchAreaBindingRemoveResponse removeAreaBinding(String areaCode, long riderId) {
        return dispatchAreaAdminModule.removeAreaBinding(areaCode, riderId);
    }

    @Override
    @Transactional
    public DispatchAreaRenameResponse renameArea(String areaCode, String newAreaCode) {
        return dispatchAreaAdminModule.renameArea(areaCode, newAreaCode);
    }

    @Override
    @Transactional
    public DispatchAreaDeleteResponse deleteArea(String areaCode) {
        return dispatchAreaAdminModule.deleteArea(areaCode);
    }

    @Override
    public List<DispatchReassignmentResponse> recentReassignments(String serveDate) {
        return dispatchQueryModule.recentReassignments(serveDate);
    }

    @Override
    @Transactional
    public DispatchAreaRiderAssignResponse assignRiderToArea(String areaCode, String riderName, String updatedBy, String mealPeriod) {
        return dispatchAssignmentModule.assignRiderToArea(areaCode, riderName, mealPeriod);
    }

    @Override
    @Transactional
    public DispatchAreaOrderAssignResponse assignRiderToAreaOrder(String areaCode, long orderId, String riderName, String updatedBy) {
        return dispatchAssignmentModule.assignRiderToAreaOrder(areaCode, orderId, riderName);
    }

    @Override
    @Transactional
    public DispatchAreaOrdersReorderResponse reorderAreaOrders(String areaCode, List<DispatchOrderReorderItemRequest> items) {
        return dispatchAssignmentModule.reorderAreaOrders(areaCode, items);
    }

    @Override
    @Transactional
    public DispatchOrderAreaMoveResponse moveOrderToArea(String areaCode, long orderId, String targetAreaCode, String updatedBy) {
        return dispatchAssignmentModule.moveOrderToArea(areaCode, orderId, targetAreaCode, updatedBy);
    }

    @Override
    @Transactional
    public DispatchReassignResultResponse reassignDispatch(
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
    ) {
        return dispatchAssignmentModule.reassignDispatch(
            reassignLevel,
            targetId,
            fromRiderName,
            toRiderName,
            toAreaCode,
            serveDate,
            mealPeriod,
            syncDefaultBinding,
            reason,
            createdBy,
            (areaCode, keywords, defaultRiderId, backupRiderId, updatedBy) ->
                updateAreaBinding(areaCode, keywords, defaultRiderId, backupRiderId, updatedBy)
        );
    }

    @Override
    @Transactional
    public DispatchRiderActivateResponse activateRider(long riderId, String riderName, String areaCode, String assignedBy) {
        return dispatchRiderAdminModule.activateRider(
            riderId,
            riderName,
            areaCode,
            assignedBy,
            (bindingAreaCode, keywords, defaultRiderId, backupRiderId, operator) ->
                updateAreaBinding(bindingAreaCode, keywords, defaultRiderId, backupRiderId, operator)
        );
    }

    @Override
    @Transactional
    public DispatchRiderStatusResponse disableRider(long riderId, String assignedBy) {
        return dispatchRiderAdminModule.disableRider(riderId, assignedBy);
    }

    @Override
    @Transactional
    public DispatchAreaAiCorrectionPreviewResponse previewAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionPreviewRequest request,
        String operatorName
    ) {
        return dispatchAreaCorrectionModule.previewAreaAiCorrection(areaCode, request, operatorName);
    }

    @Override
    @Transactional
    public DispatchAreaAiCorrectionPreviewResponse confirmAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionConfirmRequest request,
        String operatorName
    ) {
        return dispatchAreaCorrectionModule.confirmAreaAiCorrection(areaCode, request, operatorName);
    }

    @Override
    @Transactional
    public DispatchRouteSuggestionResponse suggestAreaRoute(String areaCode, DispatchRouteSuggestionRequest request) {
        return dispatchRouteSuggestionModule.suggestAreaRoute(areaCode, request);
    }

    @Override
    @Transactional
    public DispatchRouteSuggestionResponse simulateRouteLab(DispatchRouteLabSimulateRequest request, String operatorName) {
        return dispatchRouteLabModule.simulateRouteLab(request, operatorName);
    }

    @Override
    public DispatchRouteLabStartResponse startRouteLabSimulation(DispatchRouteLabSimulateRequest request, String operatorName) {
        return dispatchRouteLabModule.startRouteLabSimulation(request, operatorName);
    }

    @Override
    public DispatchAiJobLogResponse getDispatchAiJobLog(long logId) {
        return dispatchRouteLabModule.getDispatchAiJobLog(logId);
    }

    @Override
    @Transactional
    public void deleteJobLogs(List<Long> ids) {
        dispatchRouteLabModule.deleteJobLogs(ids);
    }

    @Override
    @Transactional
    public DispatchRouteSuggestionFeedbackResponse saveRouteSuggestionFeedback(
        String areaCode,
        DispatchRouteSuggestionFeedbackRequest request,
        String operatorName
    ) {
        return dispatchRouteSuggestionModule.saveRouteSuggestionFeedback(areaCode, request, operatorName);
    }

    @Override
    @Transactional
    public void preRouteTomorrowAreas() {
        dispatchRouteSuggestionModule.preRouteTomorrowAreas();
    }
}
