package com.jzqs.app.mobile.api;

public record MobileOrderItemResponse(
    long id,
    String serveDate,
    String mealPeriod,
    String mealName,
    String mealDetail,
    String merchantNote,
    String note,
    String deliveryAddress,
    String source,
    String status,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    boolean receiptVisible,
    boolean afterSaleOpen,
    String afterSaleStatus,
    String afterSaleType,
    String afterSaleAdminRemark
) {
}
