package com.jzqs.app.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @Size(max = 64, message = "username长度不能超过64")
    String username,

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    String displayName,

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^[0-9]{11}$", message = "手机号必须是11位数字")
    String phone,

    @NotBlank(message = "角色不能为空")
    @Size(max = 32, message = "角色长度不能超过32")
    String role,

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为6-32位")
    String password
) {
}
