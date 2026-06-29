package com.jzqs.app.settings.api;

public record BannerImageUploadResponse(
    String url,
    String fileKey,
    long size
) {
}
