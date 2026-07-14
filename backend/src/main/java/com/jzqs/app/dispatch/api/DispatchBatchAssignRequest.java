package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DispatchBatchAssignRequest(
    @NotEmpty(message = "请选择要分配的订单") List<Long> orderIds,
    @NotBlank(message = "请选择区域") String areaCode,
    String updatedBy
) {
}
