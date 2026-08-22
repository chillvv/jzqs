package com.jzqs.app.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    String displayName,

    @NotBlank(message = "角色不能为空")
    @Size(max = 32, message = "角色长度不能超过32")
    String role,

    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过16")
    String status
) {
}
