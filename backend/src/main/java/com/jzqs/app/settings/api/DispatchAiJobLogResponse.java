package com.jzqs.app.settings.api;

public record DispatchAiJobLogResponse(
    long id,
    String runType,
    String triggerSource,
    String serveDate,
    String mealPeriod,
    String areaCode,
    Long suggestionId,
    String status,
    String suggestionSource,
    String reasonSummary,
    String message,
    String metadataJson,
    String executedBy,
    String startedAt,
    String finishedAt
) {
}
