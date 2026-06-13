package com.jzqs.app.mobile.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 骑手注册请求
 */
@Schema(description = "骑手注册请求")
public record RiderRegisterRequest(
    
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    @Schema(description = "手机号", example = "13800138000")
    String phone,
    
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 20, message = "姓名长度需在2到20个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·\\s]{2,20}$", message = "请输入正确的骑手姓名")
    @Schema(description = "骑手姓名", example = "张三")
    String name,
    
    @Schema(description = "微信 openid", example = "rider_dev_xxx")
    String openid
) {
}
