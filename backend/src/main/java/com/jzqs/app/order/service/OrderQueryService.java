package com.jzqs.app.order.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.util.List;

public interface OrderQueryService {
    List<SubscriptionPreviewItem> subscriptionPreview(String serveDate);

    SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate);

    OrderPrepStatsResponse prepStats();

    PageResponse<OrderPrepItemResponse> prepPage(String serveDate);

    List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate);

    OrderNotesResponse orderNotes(long orderId);

    List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword);
}
