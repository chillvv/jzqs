package com.jzqs.app.aftersale.api;

public record AdminAftersaleListItemResponse(
    long id,
    long orderId,
    long customerId,
    String customerName,
    String customerPhone,
    String serveDate,
    String mealPeriod,
    String orderStatus,
    String type,
    String status,
    String source,
    String sourceCategory,
    String reasonCode,
    String reasonText,
    String issueParamSummary,
    int estimatedLossMeals,
    int settledLossMeals,
    int walletDelta,
    int giftZeroMealCount,
    int giftVeggieJuiceCount,
    String resolutionAction,
    boolean refundBlocking,
    String adminRemark,
    String requestedAt,
    String processedAt
) {
}
