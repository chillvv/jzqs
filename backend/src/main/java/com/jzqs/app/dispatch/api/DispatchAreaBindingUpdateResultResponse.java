package com.jzqs.app.dispatch.api;

public record DispatchAreaBindingUpdateResultResponse(
    String areaCode,
    String keywords,
    Long defaultRiderId,
    Long backupRiderId,
    String status
) {
}
