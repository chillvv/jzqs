package com.jzqs.app.mobile.api;

public record RiderDeliveryUploadResponse(
    String fileKey,
    String previewUrl,
    long size
) {
}
