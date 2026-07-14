
package com.jzqs.app.dispatch.api;

import java.util.List;

public record DispatchRouteSuggestionResponse(
    long suggestionId,
    String strategyMode,
    String suggestionSource,
    String reasonSummary,
    String runStatusCode,
    String runStatusLabel,
    String runStatusDescription,
    List<DispatchRouteSuggestionItemResponse> items
) {}
