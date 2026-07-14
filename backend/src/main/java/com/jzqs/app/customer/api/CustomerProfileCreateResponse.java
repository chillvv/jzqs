package com.jzqs.app.customer.api;

public record CustomerProfileCreateResponse(
    long customerId,
    String status
) {
}
