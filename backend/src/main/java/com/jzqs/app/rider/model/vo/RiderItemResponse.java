package com.jzqs.app.rider.model.vo;

public record RiderItemResponse(
    Long id,
    String riderName,
    String displayName,
    String phone,
    String authStatus,
    String employmentStatus,
    String defaultAreaCode,
    String assignedBy
) {
}
