package com.jzqs.app.order;
public record MealSlotOrder(MealPeriod mealPeriod, int quantity, long addressId, String note, OrderStatus status) {
}
