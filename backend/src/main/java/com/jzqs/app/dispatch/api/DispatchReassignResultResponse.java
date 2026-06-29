package com.jzqs.app.dispatch.api;

public record DispatchReassignResultResponse(
    String reassignLevel,
    long targetId,
    String toRiderName,
    String toAreaCode,
    boolean syncDefaultBinding,
    int affectedOrderCount
) {}
