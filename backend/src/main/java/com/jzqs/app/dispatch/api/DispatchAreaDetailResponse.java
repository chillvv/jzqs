package com.jzqs.app.dispatch.api;

import java.util.List;

public record DispatchAreaDetailResponse(
    String areaCode,
    String currentRiderName,
    int orderCount,
    boolean missingRider,
    List<DispatchAreaOrderItemResponse> orders
) {
}
