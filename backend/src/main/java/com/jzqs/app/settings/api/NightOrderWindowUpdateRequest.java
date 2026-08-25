package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NightOrderWindowUpdateRequest(
    @NotBlank(message = "nightOrderCutoffTime is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "nightOrderCutoffTime must be HH:mm")
    String nightOrderCutoffTime,
    @NotBlank(message = "nightOrderOpenTime is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "nightOrderOpenTime must be HH:mm")
    String nightOrderOpenTime
) {
}
