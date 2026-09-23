package com.jzqs.app.dispatch.api;

import java.math.BigDecimal;

public record DispatchRiderProfileUpsertResponse(
    long riderId,
    String riderName,
    String displayName,
    String phone,
    String areaCode,
    String riderStatus,
    BigDecimal monthlySalary
) {
}
