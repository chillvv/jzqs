package com.jzqs.app.mobile.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 骑手手机号登录请求
 */
@Schema(description = "骑手手机号登录请求")
public record RiderPhoneLoginRequest(
    
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    @Schema(description = "手机号", example = "13800138000")
    String phone,
    
    @Schema(description = "微信 openid", example = "rider_dev_xxx")
    String openid
) {
}
