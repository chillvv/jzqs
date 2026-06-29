package com.jzqs.app.customer.api;

public record CustomerAddressActionResponse(
    long customerId,
    long addressId,
    String status
) {
}
