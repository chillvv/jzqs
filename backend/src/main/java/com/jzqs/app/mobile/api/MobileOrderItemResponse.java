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
    String userVisibleStatus,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    boolean receiptVisible,
    boolean canChangeAddress,
    String changeAddressMode,
    boolean afterSaleOpen,
    String afterSaleStatus,
    String afterSaleType,
    String afterSaleAdminRemark
) {
}
