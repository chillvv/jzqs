package com.jzqs.app.order.api;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveResponse;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/orders")
public class OrderPrepController {
    private final OrderPrepService orderPrepService;
    private final AftersaleService aftersaleService;

    public OrderPrepController(OrderPrepService orderPrepService, AftersaleService aftersaleService) {
        this.orderPrepService = orderPrepService;
        this.aftersaleService = aftersaleService;
    }

    @GetMapping("/subscription-preview")
    public ApiResponse<List<SubscriptionPreviewItem>> subscriptionPreview(@RequestParam String serveDate) {
        return ApiResponse.success(orderPrepService.subscriptionPreview(serveDate));
    }

    @PostMapping("/subscription-preview-check")
    public ApiResponse<SubscriptionPreviewCheckResponse> subscriptionPreviewCheck(
        @Valid @RequestBody SubscriptionPreviewCheckRequest request
    ) {
        return ApiResponse.success(orderPrepService.subscriptionPreviewCheck(request.serveDate()));
    }

    @PostMapping("/bulk-import-subscription")
    @RateLimit(key = "admin:orders:bulk-import-subscription", maxRequests = 2, windowSeconds = 20)
    @Idempotent(key = "admin:orders:bulk-import-subscription", ttlSeconds = 15, includeBody = true)
    @AuditAction(module = "ORDER", action = "BULK_IMPORT_SUBSCRIPTION")
    public ApiResponse<SubscriptionBulkImportResponse> bulkImportSubscription(@Valid @RequestBody BulkImportSubscriptionRequest request) {
        return ApiResponse.success(orderPrepService.bulkImportSubscription(request.serveDate(), request.items()));
    }

    @GetMapping("/prep-stats")
    public ApiResponse<OrderPrepStatsResponse> prepStats() {
        return ApiResponse.success(orderPrepService.prepStats());
    }

    @GetMapping("/subscription-confirmations")
    public ApiResponse<List<SubscriptionConfirmationItem>> subscriptionConfirmations(@RequestParam String serveDate) {
        return ApiResponse.success(orderPrepService.subscriptionConfirmations(serveDate));
    }

    @PostMapping("/subscription-confirmations/{confirmationId}/confirm")
    @RateLimit(key = "admin:orders:subscription-confirm", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:subscription-confirm", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "ORDER", action = "CONFIRM_SUBSCRIPTION")
    public ApiResponse<SubscriptionActionResponse> confirmSubscription(@PathVariable long confirmationId) {
        return ApiResponse.success(orderPrepService.confirmSubscription(confirmationId));
    }

    @PostMapping("/subscription-confirmations/{confirmationId}/cancel")
    @RateLimit(key = "admin:orders:subscription-cancel", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:subscription-cancel", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "ORDER", action = "CANCEL_SUBSCRIPTION")
    public ApiResponse<SubscriptionActionResponse> cancelSubscription(
        @PathVariable long confirmationId,
        @RequestBody(required = false) SubscriptionCancelRequest request
    ) {
        String cancelReason = request == null ? "" : request.cancelReason();
        return ApiResponse.success(orderPrepService.cancelSubscription(confirmationId, cancelReason));
    }

    @GetMapping("/manual-create/customers")
    public ApiResponse<List<ManualCreateCustomerSearchResponse>> searchManualCreateCustomers(@RequestParam String keyword) {
        return ApiResponse.success(orderPrepService.searchManualCreateCustomers(keyword));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderPrepItemResponse>> list(
        @RequestParam(required = false) String serveDate
    ) {
        return ApiResponse.success(orderPrepService.prepPage(serveDate));
    }

    @PostMapping("/{orderId}/merchant-remark")
    @RateLimit(key = "admin:orders:merchant-remark", maxRequests = 6, windowSeconds = 10)
    @Idempotent(key = "admin:orders:merchant-remark", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "ORDER", action = "UPDATE_MERCHANT_REMARK")
    public ApiResponse<OrderMerchantRemarkUpdateResponse> updateMerchantRemark(
        @PathVariable long orderId,
        @Valid @RequestBody OrderMerchantRemarkUpdateRequest request
    ) {
        return ApiResponse.success(orderPrepService.updateMerchantRemark(orderId, request));
    }

    @PostMapping("/{orderId}/profile")
    @RateLimit(key = "admin:orders:update-profile", maxRequests = 6, windowSeconds = 10)
    @Idempotent(key = "admin:orders:update-profile", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "ORDER", action = "UPDATE_PROFILE")
    public ApiResponse<OrderProfileUpdateResponse> updateOrderProfile(
        @PathVariable long orderId,
        @Valid @RequestBody OrderProfileUpdateRequest request
    ) {
        return ApiResponse.success(orderPrepService.updateOrderProfile(orderId, request));
    }

    @PostMapping("/{orderId}/special-dispatch")
    @RateLimit(key = "admin:orders:special-dispatch", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:special-dispatch", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "ORDER", action = "SET_SPECIAL_DISPATCH")
    public ApiResponse<OrderSpecialDispatchResponse> updateSpecialDispatch(
        @PathVariable long orderId,
        @Valid @RequestBody OrderSpecialDispatchUpdateRequest request
    ) {
        return ApiResponse.success(orderPrepService.updateSpecialDispatch(orderId, request.deliveryMealPeriod()));
    }

    @PostMapping("/{orderId}/special-dispatch/reset")
    @RateLimit(key = "admin:orders:special-dispatch-reset", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:special-dispatch-reset", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "ORDER", action = "RESET_SPECIAL_DISPATCH")
    public ApiResponse<OrderSpecialDispatchResponse> resetSpecialDispatch(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.resetSpecialDispatch(orderId));
    }

    @GetMapping("/{orderId}/notes")
    public ApiResponse<OrderNotesResponse> orderNotes(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.orderNotes(orderId));
    }

    @PostMapping("/{orderId}/notes")
    @RateLimit(key = "admin:orders:add-note", maxRequests = 6, windowSeconds = 10)
    @Idempotent(key = "admin:orders:add-note", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "ORDER", action = "ADD_NOTE")
    public ApiResponse<OrderNoteCreateResponse> addOrderNote(
        @PathVariable long orderId,
        @Valid @RequestBody OrderNoteCreateRequest request
    ) {
        return ApiResponse.success(orderPrepService.addOrderNote(orderId, request));
    }

    @PostMapping("/manual-create")
    @RateLimit(key = "admin:orders:manual-create", maxRequests = 3, windowSeconds = 15)
    @Idempotent(key = "admin:orders:manual-create", ttlSeconds = 10, includeBody = true)
    @AuditAction(module = "ORDER", action = "MANUAL_CREATE")
    public ApiResponse<ManualCreateOrderResponse> manualCreate(@Valid @RequestBody ManualCreateOrderRequest request) {
        return ApiResponse.success(orderPrepService.manualCreate(
            request.customerId(),
            request.addressId(),
            request.mealPeriod(),
            request.deliveryMealPeriod(),
            request.merchantRemark(),
            request.deliveryAddress(),
            request.source(),
            request.quantityOrDefault(),
            request.serveDate()
        ));
    }

    @PostMapping("/{orderId}/cancel")
    @RateLimit(key = "admin:orders:cancel", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:orders:cancel", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "ORDER", action = "CANCEL")
    public ApiResponse<OrderActionResponse> cancel(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.cancelOrder(orderId));
    }

    @PostMapping("/{orderId}/after-sales")
    @RateLimit(key = "admin:orders:after-sale", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:after-sale", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "AFTERSALE", action = "CREATE")
    public ApiResponse<AdminAftersaleCreateResponse> createAfterSale(@PathVariable long orderId, @RequestBody OrderCreateAfterSaleRequest body) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        return ApiResponse.success(aftersaleService.createCase(new AdminAftersaleCreateRequest(
            orderId,
            body.typeOrDefault(),
            body.reasonCodeOrDefault(),
            body.reasonTextOrDefault(),
            body.issueParamSummaryOrDefault(),
            0,
            "NORMAL",
            body.remarkOrDefault(),
            operatorName
        )));
    }

    @PostMapping("/{orderId}/direct-refund")
    @RateLimit(key = "admin:orders:direct-refund", maxRequests = 2, windowSeconds = 15)
    @Idempotent(key = "admin:orders:direct-refund", ttlSeconds = 10, includeBody = true)
    @AuditAction(module = "AFTERSALE", action = "DIRECT_REFUND")
    public ApiResponse<AdminAftersaleResolveResponse> directRefund(@PathVariable long orderId, @RequestBody OrderDirectRefundRequest body) {
        String operatorName = AdminRequestContextSupport.requireOperatorName();
        AdminAftersaleCreateResponse created = aftersaleService.createCase(new AdminAftersaleCreateRequest(
            orderId,
            "REFUND",
            body.reasonCodeOrDefault(),
            body.reasonTextOrDefault(),
            body.reasonTextOrDefault(),
            0,
            "NORMAL",
            body.reasonTextOrDefault(),
            operatorName
        ));
        return ApiResponse.success(aftersaleService.resolveCase(created.afterSaleId(), new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            1,
            1,
            0,
            0,
            body.reasonTextOrDefault(),
            operatorName
        )));
    }

    @PostMapping("/{orderId}/delete")
    @RateLimit(key = "admin:orders:delete", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:orders:delete", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "ORDER", action = "DELETE")
    public ApiResponse<OrderActionResponse> delete(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.deleteOrder(orderId));
    }

    @PostMapping("/{orderId}/receipt/delete")
    @RateLimit(key = "admin:orders:delete-receipt", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:orders:delete-receipt", ttlSeconds = 5, includeBody = false)
    @AuditAction(module = "ORDER", action = "DELETE_RECEIPT")
    public ApiResponse<DeliveryReceiptDeleteResponse> deleteReceipt(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.deleteDeliveryReceipt(orderId));
    }

    @PostMapping("/consume")
    @RateLimit(key = "admin:orders:consume", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:orders:consume", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "ORDER", action = "CONSUME")
    public ApiResponse<BatchOperationResponse> consume(@Valid @RequestBody BatchConsumeOrdersRequest request) {
        return ApiResponse.success(orderPrepService.consumeOrders(request.orderIds()));
    }
}
