package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DispatchDeleteJobLogsRequest(
    @NotEmpty(message = "日志 ID 列表不能为空")
    List<Long> ids
) {}
