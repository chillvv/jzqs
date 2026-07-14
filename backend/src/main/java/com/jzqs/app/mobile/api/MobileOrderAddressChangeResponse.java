package com.jzqs.app.mobile.api;

public record MobileOrderAddressChangeResponse(
    long orderId,
    long addressId,
    String status
) {
}
