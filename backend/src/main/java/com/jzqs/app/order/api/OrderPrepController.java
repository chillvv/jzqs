package com.jzqs.app.order.api;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
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
    public ApiResponse<SubscriptionPreviewCheckResponse> subscriptionPreviewCheck(@RequestBody Map<String, String> body) {
        return ApiResponse.success(orderPrepService.subscriptionPreviewCheck(body.get("serveDate")));
    }

    @PostMapping("/bulk-import-subscription")
    public ApiResponse<Map<String, Object>> bulkImportSubscription(@Valid @RequestBody BulkImportSubscriptionRequest request) {
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
    public ApiResponse<Map<String, Object>> confirmSubscription(@PathVariable long confirmationId) {
        return ApiResponse.success(orderPrepService.confirmSubscription(confirmationId));
    }

    @PostMapping("/subscription-confirmations/{confirmationId}/cancel")
    public ApiResponse<Map<String, Object>> cancelSubscription(@PathVariable long confirmationId, @RequestBody Map<String, String> body) {
        return ApiResponse.success(orderPrepService.cancelSubscription(confirmationId, body.getOrDefault("cancelReason", "")));
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
    public ApiResponse<Map<String, Object>> updateMerchantRemark(@PathVariable long orderId, @RequestBody Map<String, String> body) {
        return ApiResponse.success(orderPrepService.updateMerchantRemark(
            orderId,
            body.getOrDefault("merchantRemark", "")
        ));
    }

    @PostMapping("/{orderId}/profile")
    public ApiResponse<Map<String, Object>> updateOrderProfile(@PathVariable long orderId, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(orderPrepService.updateOrderProfile(orderId, body));
    }

    @PostMapping("/{orderId}/special-dispatch")
    public ApiResponse<Map<String, Object>> updateSpecialDispatch(@PathVariable long orderId, @RequestBody Map<String, String> body) {
        return ApiResponse.success(orderPrepService.updateSpecialDispatch(orderId, body.get("deliveryMealPeriod")));
    }

    @PostMapping("/{orderId}/special-dispatch/reset")
    public ApiResponse<Map<String, Object>> resetSpecialDispatch(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.resetSpecialDispatch(orderId));
    }

    @GetMapping("/{orderId}/notes")
    public ApiResponse<OrderNotesResponse> orderNotes(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.orderNotes(orderId));
    }

    @PostMapping("/{orderId}/notes")
    public ApiResponse<Map<String, Object>> addOrderNote(
        @PathVariable long orderId,
        @Valid @RequestBody OrderNoteCreateRequest request
    ) {
        return ApiResponse.success(orderPrepService.addOrderNote(orderId, request));
    }

    @PostMapping("/manual-create")
    public ApiResponse<Map<String, Object>> manualCreate(@Valid @RequestBody ManualCreateOrderRequest request) {
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
    public ApiResponse<Map<String, Object>> cancel(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.cancelOrder(orderId));
    }

    @PostMapping("/{orderId}/after-sales")
    public ApiResponse<Map<String, Object>> createAfterSale(@PathVariable long orderId, @RequestBody Map<String, String> body) {
        return ApiResponse.success(aftersaleService.createCase(new AdminAftersaleCreateRequest(
            orderId,
            body.getOrDefault("type", "COMPENSATION"),
            body.getOrDefault("reasonCode", "ADMIN_DIRECT"),
            body.getOrDefault("reasonText", ""),
            body.getOrDefault("issueParamSummary", ""),
            0,
            "NORMAL",
            body.getOrDefault("remark", ""),
            body.getOrDefault("operatorName", "后台客服")
        )));
    }

    @PostMapping("/{orderId}/direct-refund")
    public ApiResponse<Map<String, Object>> directRefund(@PathVariable long orderId, @RequestBody Map<String, String> body) {
        Map<String, Object> created = aftersaleService.createCase(new AdminAftersaleCreateRequest(
            orderId,
            "REFUND",
            body.getOrDefault("reasonCode", "ADMIN_DIRECT_REFUND"),
            body.getOrDefault("reasonText", "商家后台直接退款"),
            body.getOrDefault("reasonText", "商家后台直接退款"),
            0,
            "NORMAL",
            body.getOrDefault("reasonText", "商家后台直接退款"),
            body.getOrDefault("operatorName", "后台客服")
        ));
        Number afterSaleId = (Number) created.get("afterSaleId");
        return ApiResponse.success(aftersaleService.resolveCase(afterSaleId.longValue(), new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            true,
            1,
            1,
            0,
            0,
            body.getOrDefault("reasonText", "商家后台直接退款"),
            body.getOrDefault("operatorName", "后台客服")
        )));
    }

    @PostMapping("/{orderId}/delete")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.deleteOrder(orderId));
    }

    @PostMapping("/{orderId}/receipt/delete")
    public ApiResponse<Map<String, Object>> deleteReceipt(@PathVariable long orderId) {
        return ApiResponse.success(orderPrepService.deleteDeliveryReceipt(orderId));
    }

    @PostMapping("/consume")
    public ApiResponse<BatchOperationResponse> consume(@Valid @RequestBody BatchConsumeOrdersRequest request) {
        return ApiResponse.success(orderPrepService.consumeOrders(request.orderIds()));
    }
}
