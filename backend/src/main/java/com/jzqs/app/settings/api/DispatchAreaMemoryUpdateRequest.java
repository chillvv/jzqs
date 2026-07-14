package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchAreaMemoryUpdateRequest(
    @NotBlank(message = "title is required")
    String title,
    @NotBlank(message = "summary is required")
    String summary,
    @NotBlank(message = "applicableScene is required")
    String applicableScene,
    @NotBlank(message = "status is required")
    String status
) {
}
