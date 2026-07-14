package com.jzqs.app.order.service;

import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.api.OrderSpecialDispatchResponse;

public interface OrderDispatchService {
    OrderSpecialDispatchResponse updateSpecialDispatch(long orderId, String deliveryMealPeriod);

    OrderSpecialDispatchResponse resetSpecialDispatch(long orderId);

    DeliveryReceiptDeleteResponse deleteDeliveryReceipt(long orderId);
}
