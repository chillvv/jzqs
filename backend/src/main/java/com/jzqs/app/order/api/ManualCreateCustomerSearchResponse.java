package com.jzqs.app.order.api;

import java.util.List;

public record ManualCreateCustomerSearchResponse(
    long customerId,
    String customerName,
    String customerPhone,
    int remainingMeals,
    List<ManualCreateCustomerAddressResponse> addresses
) {
}
