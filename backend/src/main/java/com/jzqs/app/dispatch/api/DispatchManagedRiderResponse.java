package com.jzqs.app.dispatch.api;

import java.math.BigDecimal;

public record DispatchManagedRiderResponse(
    long riderId,
    String riderName,
    String displayName,
    String phone,
    String authStatus,
    String employmentStatus,
    String areaCode,
    String assignedBy,
    String firstLoginAt,
    String lastLoginAt,
    int todayTaskCount,
    int todayDeliveredCount,
    String currentOpenid,
    BigDecimal monthlySalary
) {
}
