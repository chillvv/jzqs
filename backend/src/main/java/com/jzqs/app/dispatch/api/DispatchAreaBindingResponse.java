package com.jzqs.app.dispatch.api;

import java.util.List;

public record DispatchAreaBindingResponse(
    String areaCode,
    String keywords,
    Long defaultRiderId,
    String defaultRiderName,
    String currentRiderName,
    int orderCount,
    boolean missingRider,
    List<DispatchAreaOrderItemResponse> orders,
    String updatedBy,
    String updatedAt
) {
}
