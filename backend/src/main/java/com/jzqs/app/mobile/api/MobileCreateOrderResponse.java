package com.jzqs.app.mobile.api;

public record MobileCreateOrderResponse(
    long orderId,
    String status,
    String walletAction
) {
}
