package com.jzqs.app.rider.model.vo;

public record RiderDetailResponse(
    Long id,
    String riderName,
    String displayName,
    String phone,
    String authStatus,
    String employmentStatus,
    String defaultAreaCode,
    String remark,
    String assignedBy,
    String createdAt,
    String updatedAt,
    String firstLoginAt,
    String lastLoginAt
) {
}
