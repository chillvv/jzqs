package com.jzqs.app.order.service.impl;

import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.service.OrderOperationService;
import com.jzqs.app.order.service.OrderSubscriptionImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSubscriptionImportExecutor implements OrderSubscriptionImportService {
    private final OrderOperationService orderOperationService;

    public OrderSubscriptionImportExecutor(OrderOperationService orderOperationService) {
        this.orderOperationService = orderOperationService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importSingleItem(SubscriptionImportItem item, String serveDate, String addressLine) {
        orderOperationService.manualCreate(
            item.customerId(),
            item.addressId(),
            item.mealPeriod(),
            item.deliveryMealPeriod(),
            item.note(),
            addressLine,
            "SUBSCRIPTION",
            1,
            serveDate
        );
    }
}
