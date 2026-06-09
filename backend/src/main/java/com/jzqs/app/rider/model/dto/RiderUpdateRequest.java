package com.jzqs.app.rider.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RiderUpdateRequest(
    @NotBlank(message = "riderName不能为空")
    @Size(max = 64, message = "riderName长度不能超过64")
    String riderName,

    @NotBlank(message = "displayName不能为空")
    @Size(max = 64, message = "displayName长度不能超过64")
    String displayName,

    @NotBlank(message = "phone不能为空")
    @Pattern(regexp = "^[0-9]{11}$", message = "phone必须是11位数字")
    String phone,

    String areaCode,

    String employmentStatus,

    String authStatus,

    String remark
) {
}
