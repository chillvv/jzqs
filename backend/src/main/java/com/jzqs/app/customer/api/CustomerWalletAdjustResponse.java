package com.jzqs.app.customer.api;

public record CustomerWalletAdjustResponse(
    long customerId,
    int remainingMeals,
    String status
) {
}
