package com.jzqs.app.user.model.vo;

public record UserItemResponse(
    Long id,
    String username,
    String displayName,
    String phone,
    String role,
    String status
) {
}
