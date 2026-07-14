package com.jzqs.app.order.service;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.api.OrderNoteCreateRequest;
import com.jzqs.app.order.api.OrderNoteCreateResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.OrderProfileUpdateRequest;
import com.jzqs.app.order.api.OrderProfileUpdateResponse;
import com.jzqs.app.order.api.OrderSpecialDispatchResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.util.List;
import java.util.Map;

public interface OrderPrepService {
    List<SubscriptionPreviewItem> subscriptionPreview(String serveDate);
    
    SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate);
    
    SubscriptionBulkImportResponse bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items);

    OrderPrepStatsResponse prepStats();

    PageResponse<OrderPrepItemResponse> prepPage(String serveDate);

    List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate);

    SubscriptionActionResponse confirmSubscription(long confirmationId);

    SubscriptionActionResponse cancelSubscription(long confirmationId, String cancelReason);

    OrderMerchantRemarkUpdateResponse updateMerchantRemark(long orderId, OrderMerchantRemarkUpdateRequest request);

    OrderProfileUpdateResponse updateOrderProfile(long orderId, OrderProfileUpdateRequest request);

    OrderSpecialDispatchResponse updateSpecialDispatch(long orderId, String deliveryMealPeriod);

    OrderSpecialDispatchResponse resetSpecialDispatch(long orderId);

    OrderNotesResponse orderNotes(long orderId);

    OrderNoteCreateResponse addOrderNote(long orderId, OrderNoteCreateRequest request);

    List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword);

    ManualCreateOrderResponse manualCreate(long customerId, Long addressId, String mealPeriod, String deliveryMealPeriod, String merchantRemark, String deliveryAddress, String source, int quantity, String serveDate);

    OrderActionResponse cancelOrder(long orderId);

    DeliveryReceiptDeleteResponse deleteDeliveryReceipt(long orderId);

    OrderActionResponse deleteOrder(long orderId);

    BatchOperationResponse consumeOrders(List<Long> orderIds);
}
