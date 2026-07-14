
package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotNull;

public record DispatchRouteSuggestionFeedbackRequest(
    @NotNull long suggestionId,
    boolean acceptedDirectly,
    int changeCount,
    String feedbackSummary
) {}
