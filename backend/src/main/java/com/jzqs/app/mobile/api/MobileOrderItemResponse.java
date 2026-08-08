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
    long addressId,
    String source,
    String status,
    String userVisibleStatus,
    int quantity,
    String riderName,
    String riderPhone,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    boolean receiptVisible,
    boolean receiptEverExisted,
    boolean canChangeAddress,
    String changeAddressMode,
    boolean afterSaleOpen,
    String afterSaleStatus,
    String afterSaleType,
    String afterSaleAdminRemark
) {
}
