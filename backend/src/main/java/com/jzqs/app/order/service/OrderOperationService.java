package com.jzqs.app.order.service;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.api.OrderNoteCreateRequest;
import com.jzqs.app.order.api.OrderNoteCreateResponse;
import com.jzqs.app.order.api.OrderProfileUpdateRequest;
import com.jzqs.app.order.api.OrderProfileUpdateResponse;
import java.util.List;

public interface OrderOperationService {
    OrderMerchantRemarkUpdateResponse updateMerchantRemark(long orderId, OrderMerchantRemarkUpdateRequest request);

    OrderProfileUpdateResponse updateOrderProfile(long orderId, OrderProfileUpdateRequest request);

    OrderNoteCreateResponse addOrderNote(long orderId, OrderNoteCreateRequest request);

    ManualCreateOrderResponse manualCreate(long customerId, Long addressId, String mealPeriod, String deliveryMealPeriod, String merchantRemark, String deliveryAddress, String source, int quantity, String serveDate);

    OrderActionResponse cancelOrder(long orderId);

    OrderActionResponse deleteOrder(long orderId);

    BatchOperationResponse consumeOrders(List<Long> orderIds);
}
