package com.jzqs.app.order.service;

import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.api.SubscriptionImportSkipItem;
import java.util.List;

public interface OrderSubscriptionService {
    SubscriptionActionResponse confirmSubscription(long confirmationId);

    SubscriptionActionResponse cancelSubscription(long confirmationId, String cancelReason);

    SubscriptionBulkImportResponse bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items);

    /** 批量记录固定订餐导入中被跳过的客户餐次（取消勾选即持久化） */
    int recordSubscriptionImportSkips(String serveDate, List<SubscriptionImportSkipItem> items);

    /** 删除某客户某餐次的跳过记录（恢复导入） */
    int removeSubscriptionImportSkip(String serveDate, long customerId, String mealPeriod);
}
