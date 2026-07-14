package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DispatchAreaAiCorrectionConfirmRequest(
    @NotNull(message = "纠偏记录不能为空")
    Long correctionId,
    @NotEmpty(message = "最终顺序不能为空")
    List<Long> finalOrderIds
) {
}
