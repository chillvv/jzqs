package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RiderExceptionReportRequest(
    @NotBlank(message = "骑手姓名不能为空")
    String riderName,
    
    @NotBlank(message = "异常类型不能为空")
    String exceptionType,
    
    String exceptionNote,
    
    List<String> exceptionImages
) {
}
