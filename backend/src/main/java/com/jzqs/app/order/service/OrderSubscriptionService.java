package com.jzqs.app.order.service;

import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import java.util.List;

public interface OrderSubscriptionService {
    SubscriptionActionResponse confirmSubscription(long confirmationId);

    SubscriptionActionResponse cancelSubscription(long confirmationId, String cancelReason);

    SubscriptionBulkImportResponse bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items);
}
