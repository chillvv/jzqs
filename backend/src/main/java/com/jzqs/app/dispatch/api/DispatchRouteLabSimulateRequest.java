package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DispatchRouteLabSimulateRequest(
    @NotEmpty(message = "测试地址列表不能为空")
    List<String> addresses,
    
    @NotBlank(message = "推演策略模式不能为空")
    String strategyMode,
    
    @NotBlank(message = "锚点地址不能为空")
    String anchorAddress
) {}
