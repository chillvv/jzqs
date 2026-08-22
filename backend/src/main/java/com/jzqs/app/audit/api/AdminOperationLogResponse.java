package com.jzqs.app.audit.api;

public record AdminOperationLogResponse(
    Long id,
    Long operatorId,
    String operatorName,
    String operatorPhone,
    String operatorRole,
    String module,
    String action,
    String httpMethod,
    String requestPath,
    String requestSummary,
    String status,
    String errorMessage,
    Long durationMs,
    String clientIp,
    String createdAt,
    String moduleLabel,
    String actionLabel,
    String targetLabel,
    String detailLabel
) {
}
