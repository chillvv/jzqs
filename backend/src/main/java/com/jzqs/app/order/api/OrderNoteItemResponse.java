package com.jzqs.app.order.api;
public record OrderNoteItemResponse(
    long id,
    String noteType,
    String sourceType,
    String scopeType,
    String content,
    String effectiveStatus,
    String createdAt
) {
}
