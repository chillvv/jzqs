package com.jzqs.app.mobile.api;

public record MobileDeliverySubscriptionAuthorizeResponse(
    long orderId,
    String status
) {
}
