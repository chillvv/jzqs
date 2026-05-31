package com.jzqs.app.common.api;

import java.util.List;
import java.util.Map;

public record BatchOperationResponse(
    int successCount,
    int failureCount,
    List<Map<String, Object>> failures
) {
}
