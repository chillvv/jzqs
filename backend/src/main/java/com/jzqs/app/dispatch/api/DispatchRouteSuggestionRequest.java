
package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DispatchRouteSuggestionRequest(
    @NotBlank String serveDate,
    @NotBlank String mealPeriod,
    @NotBlank String strategyMode,
    @NotBlank String anchorName,
    @NotBlank String anchorAddress,
    Boolean enableAiRefine
) {}
