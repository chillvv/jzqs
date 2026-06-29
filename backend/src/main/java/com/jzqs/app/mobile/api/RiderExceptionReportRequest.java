package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RiderExceptionReportRequest(
    @NotBlank(message = "异常类型不能为空")
    String exceptionType,
    
    String exceptionNote,
    
    List<String> exceptionImages
) {
}
