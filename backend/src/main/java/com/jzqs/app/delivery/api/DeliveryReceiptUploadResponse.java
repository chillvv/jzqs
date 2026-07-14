package com.jzqs.app.delivery.api;

public record DeliveryReceiptUploadResponse(
    String url,
    String fileKey,
    long size
) {
}
