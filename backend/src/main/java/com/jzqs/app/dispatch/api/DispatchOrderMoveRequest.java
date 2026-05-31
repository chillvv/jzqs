package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchOrderMoveRequest(
    @NotBlank(message = "请选择目标区域") String targetAreaCode,
    @NotBlank(message = "updatedBy is required") String updatedBy
) {
}
