package com.jzqs.app.common.api;

import java.util.List;

public record BatchOperationResponse(
    int successCount,
    int failureCount,
    List<FailureItem> failures
) {
    public record FailureItem(long targetId, String code, String message) {
    }
}
