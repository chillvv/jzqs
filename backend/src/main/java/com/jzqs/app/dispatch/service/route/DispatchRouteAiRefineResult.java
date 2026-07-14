package com.jzqs.app.dispatch.service.route;

import java.util.List;

public record DispatchRouteAiRefineResult(
    List<Long> orderIds,
    String reasonSummary,
    String suggestionSource,
    boolean aiAdjusted,
    String failureReason
) {}
