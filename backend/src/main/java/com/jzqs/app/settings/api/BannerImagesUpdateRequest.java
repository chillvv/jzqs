package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;

public record BannerImagesUpdateRequest(
    @NotBlank String bannerImages
) {
}
