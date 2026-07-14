package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchAiSettingsUpdateRequest(
    boolean autoScheduleEnabled,
    @NotBlank(message = "autoScheduleTime is required")
    String autoScheduleTime,
    @NotBlank(message = "defaultStrategyMode is required")
    String defaultStrategyMode,
    @NotBlank(message = "anchorName is required")
    String anchorName,
    @NotBlank(message = "anchorAddress is required")
    String anchorAddress,
    boolean aiEnabled,
    @NotBlank(message = "apiBaseUrl is required")
    String apiBaseUrl,
    String apiKey,
    @NotBlank(message = "aiModel is required")
    String aiModel,
    @NotBlank(message = "aiPromptTemplate is required")
    String aiPromptTemplate,
    @NotBlank(message = "lowBalanceThreshold is required")
    String lowBalanceThreshold
) {
}
