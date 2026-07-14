package com.jzqs.app.user.model.vo;

public record UserDetailResponse(
    Long id,
    String username,
    String displayName,
    String phone,
    String role,
    String status,
    String createdAt,
    String updatedAt
) {
}
