package com.jzqs.app.order.api;

public record OrderProfileUpdateResponse(long orderId, String status, long addressId) {
}
