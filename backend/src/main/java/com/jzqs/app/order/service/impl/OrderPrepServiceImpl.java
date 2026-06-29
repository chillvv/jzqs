package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.api.OrderNoteCreateRequest;
import com.jzqs.app.order.api.OrderNoteCreateResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.OrderProfileUpdateRequest;
import com.jzqs.app.order.api.OrderProfileUpdateResponse;
import com.jzqs.app.order.api.OrderSpecialDispatchResponse;
import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.service.OrderDispatchService;
import com.jzqs.app.order.service.OrderOperationService;
import com.jzqs.app.order.service.OrderPrepService;
import com.jzqs.app.order.service.OrderQueryService;
import com.jzqs.app.order.service.OrderSubscriptionService;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPrepServiceImpl implements OrderPrepService {
    private final OrderQueryService orderQueryService;
    private final OrderOperationService orderOperationService;
    private final OrderSubscriptionService orderSubscriptionService;
    private final OrderDispatchService orderDispatchService;

    public OrderPrepServiceImpl(
        OrderQueryService orderQueryService,
        OrderOperationService orderOperationService,
        OrderSubscriptionService orderSubscriptionService,
        OrderDispatchService orderDispatchService
    ) {
        this.orderQueryService = orderQueryService;
        this.orderOperationService = orderOperationService;
        this.orderSubscriptionService = orderSubscriptionService;
        this.orderDispatchService = orderDispatchService;
    }

    @Override
    public List<SubscriptionPreviewItem> subscriptionPreview(String serveDate) {
        return orderQueryService.subscriptionPreview(serveDate);
    }

    @Override
    public SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate) {
        return orderQueryService.subscriptionPreviewCheck(serveDate);
    }

    @Override
    @Transactional
    public SubscriptionBulkImportResponse bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items) {
        return orderSubscriptionService.bulkImportSubscription(serveDate, items);
    }

    @Override
    public OrderPrepStatsResponse prepStats() {
        return orderQueryService.prepStats();
    }

    @Override
    public PageResponse<OrderPrepItemResponse> prepPage(String serveDate) {
        return orderQueryService.prepPage(serveDate);
    }

    @Override
    public List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate) {
        return orderQueryService.subscriptionConfirmations(serveDate);
    }

    @Override
    @Transactional
    public SubscriptionActionResponse confirmSubscription(long confirmationId) {
        return orderSubscriptionService.confirmSubscription(confirmationId);
    }

    @Override
    @Transactional
    public SubscriptionActionResponse cancelSubscription(long confirmationId, String cancelReason) {
        return orderSubscriptionService.cancelSubscription(confirmationId, cancelReason);
    }

    @Override
    @Transactional
    public OrderMerchantRemarkUpdateResponse updateMerchantRemark(long orderId, OrderMerchantRemarkUpdateRequest request) {
        return orderOperationService.updateMerchantRemark(orderId, request);
    }

    @Override
    @Transactional
    public OrderProfileUpdateResponse updateOrderProfile(long orderId, OrderProfileUpdateRequest request) {
        return orderOperationService.updateOrderProfile(orderId, request);
    }

    @Override
    @Transactional
    public OrderSpecialDispatchResponse updateSpecialDispatch(long orderId, String deliveryMealPeriod) {
        return orderDispatchService.updateSpecialDispatch(orderId, deliveryMealPeriod);
    }

    @Override
    @Transactional
    public OrderSpecialDispatchResponse resetSpecialDispatch(long orderId) {
        return orderDispatchService.resetSpecialDispatch(orderId);
    }

    @Override
    public OrderNotesResponse orderNotes(long orderId) {
        return orderQueryService.orderNotes(orderId);
    }

    @Override
    @Transactional
    public OrderNoteCreateResponse addOrderNote(long orderId, OrderNoteCreateRequest request) {
        return orderOperationService.addOrderNote(orderId, request);
    }

    @Override
    public List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword) {
        return orderQueryService.searchManualCreateCustomers(keyword);
    }

    @Override
    @Transactional
    public ManualCreateOrderResponse manualCreate(
        long customerId,
        Long addressId,
        String mealPeriod,
        String deliveryMealPeriod,
        String merchantRemark,
        String deliveryAddress,
        String source,
        int quantity,
        String serveDate
    ) {
        return orderOperationService.manualCreate(
            customerId,
            addressId,
            mealPeriod,
            deliveryMealPeriod,
            merchantRemark,
            deliveryAddress,
            source,
            quantity,
            serveDate
        );
    }

    @Override
    @Transactional
    public OrderActionResponse cancelOrder(long orderId) {
        return orderOperationService.cancelOrder(orderId);
    }

    @Override
    @Transactional
    public DeliveryReceiptDeleteResponse deleteDeliveryReceipt(long orderId) {
        return orderDispatchService.deleteDeliveryReceipt(orderId);
    }

    @Override
    @Transactional
    public OrderActionResponse deleteOrder(long orderId) {
        return orderOperationService.deleteOrder(orderId);
    }

    @Override
    @Transactional
    public BatchOperationResponse consumeOrders(List<Long> orderIds) {
        return orderOperationService.consumeOrders(orderIds);
    }
}
