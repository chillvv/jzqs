package com.jzqs.app.order.service;

import com.jzqs.app.order.api.SubscriptionImportItem;

public interface OrderSubscriptionImportService {
    void importSingleItem(SubscriptionImportItem item, String serveDate, String addressLine);
}
