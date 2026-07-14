package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchRouteWorkbenchSettingsUpdateRequest(
    boolean autoScheduleEnabled,
    @NotBlank(message = "autoScheduleTime is required")
    String autoScheduleTime,
    @NotBlank(message = "defaultStrategyMode is required")
    String defaultStrategyMode,
    @NotBlank(message = "anchorAddress is required")
    String anchorAddress
) {
}
