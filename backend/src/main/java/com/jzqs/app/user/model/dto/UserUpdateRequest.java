package com.jzqs.app.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "displayName不能为空")
    @Size(max = 64, message = "displayName长度不能超过64")
    String displayName,

    @NotBlank(message = "phone不能为空")
    @Pattern(regexp = "^[0-9]{11}$", message = "phone必须是11位数字")
    String phone,

    @NotBlank(message = "role不能为空")
    @Size(max = 32, message = "role长度不能超过32")
    String role,

    @NotBlank(message = "status不能为空")
    @Size(max = 16, message = "status长度不能超过16")
    String status
) {
}
