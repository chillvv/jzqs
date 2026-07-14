package com.jzqs.app.dispatch.api;
public record DispatchBoardItemResponse(
    long dispatchId,
    long orderId,
    String customerName,
    String deliveryAddress,
    String riderName,
    String areaCode,
    String deliveryStatus,
    String receiptStatus,
    String receiptLabel,
    boolean canNotifyCustomer
) {
}
