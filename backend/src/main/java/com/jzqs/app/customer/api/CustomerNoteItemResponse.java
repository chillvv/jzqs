package com.jzqs.app.customer.api;
public record CustomerNoteItemResponse(
    long id,
    String noteType,
    String scopeType,
    String content,
    String startAt,
    String endAt,
    boolean active
) {
}
