package com.jzqs.app.dispatch.api;

public record DispatchAreaRenameResponse(
    String oldAreaCode,
    String newAreaCode,
    String status
) {
}
