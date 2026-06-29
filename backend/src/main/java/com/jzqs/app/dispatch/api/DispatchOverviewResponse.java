package com.jzqs.app.dispatch.api;

public record DispatchOverviewResponse(
    int pendingCount,
    int dispatchingCount,
    int missingRiderAreaCount
) {
}
