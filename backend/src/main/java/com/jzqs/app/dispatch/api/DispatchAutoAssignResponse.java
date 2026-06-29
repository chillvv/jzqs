package com.jzqs.app.dispatch.api;

public record DispatchAutoAssignResponse(
    int assignedCount,
    int exceptionCount
) {}
