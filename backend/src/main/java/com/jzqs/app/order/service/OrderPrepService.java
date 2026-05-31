package com.jzqs.app.order.service;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SpecialOrderItem;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.util.List;
import java.util.Map;

public interface OrderPrepService {
    List<SubscriptionPreviewItem> subscriptionPreview(String serveDate);
    
    SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate);
    
    Map<String, Object> bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items);

    OrderPrepStatsResponse prepStats();

    PageResponse<OrderPrepItemResponse> prepPage(String serveDate);

    List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate);

    Map<String, Object> confirmSubscription(long confirmationId);

    Map<String, Object> cancelSubscription(long confirmationId, String cancelReason);

    Map<String, Object> updateAdminNote(long orderId, String adminNote, String specialTag);

    Map<String, Object> updateOrderProfile(long orderId, Map<String, Object> payload);

    List<SpecialOrderItem> specialOrders(String serveDate);

    List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword);

    Map<String, Object> manualCreate(long customerId, Long addressId, String mealSummary, String note, String deliveryAddress, String source, int quantity, String serveDate);

    Map<String, Object> cancelOrder(long orderId);

    Map<String, Object> deleteOrder(long orderId);

    BatchOperationResponse consumeOrders(List<Long> orderIds);
}
