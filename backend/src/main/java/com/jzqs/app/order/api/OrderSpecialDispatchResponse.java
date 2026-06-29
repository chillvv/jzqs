package com.jzqs.app.order.api;

public record OrderSpecialDispatchResponse(long orderId, String status, String deliveryMealPeriod) {
}
