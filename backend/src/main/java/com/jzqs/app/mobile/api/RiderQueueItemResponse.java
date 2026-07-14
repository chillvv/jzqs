package com.jzqs.app.mobile.api;

import java.util.List;

public record RiderQueueItemResponse(
    long batchItemId,
    long batchId,
    long mealSlotOrderId,
    long addressId,
    int currentSequence,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String mealPeriod,
    String productionMealPeriod,
    String deliveryMealPeriod,
    String mealName,
    int quantity,
    String note,
    String merchantRemark,
    boolean hasAttentionMark,
    List<String> attentionSources,
    String attentionLabel,
    boolean specialOrder,
    String specialSummary,
    String itemStatus,
    String receiptStatus,
    String receiptUrl,
    String receiptNote
) {
}
