package com.jzqs.app.customer.api;

public record CustomerProfileUpdateResponse(
    long customerId,
    String status
) {
}
