package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DispatchAreaAiCorrectionPreviewRequest(
    Long routeRunId,
    @NotEmpty(message = "原始顺序不能为空")
    List<Long> originalOrderIds,
    @NotEmpty(message = "商家顺序不能为空")
    List<Long> merchantOrderIds,
    @NotEmpty(message = "地址快照不能为空")
    List<String> inputAddresses,
    String merchantInstruction,
    String merchantReasonSummary
) {
}
