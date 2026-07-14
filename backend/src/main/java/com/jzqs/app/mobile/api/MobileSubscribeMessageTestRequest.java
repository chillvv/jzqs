package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;

public record MobileSubscribeMessageTestRequest(
    @NotBlank(message = "templateId is required")
    String templateId,
    @NotBlank(message = "acceptResult is required")
    String acceptResult
) {
}
