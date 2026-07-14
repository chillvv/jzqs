package com.jzqs.app.mobile.api;

public record RiderReceiptRequest(
    String receiptFileKey,
    String receiptNote,
    String deliveredAt
) {
}
