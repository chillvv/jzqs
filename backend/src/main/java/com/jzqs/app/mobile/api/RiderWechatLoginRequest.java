package com.jzqs.app.mobile.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 骑手微信一键登录请求
 */
@Schema(description = "骑手微信一键登录请求")
public record RiderWechatLoginRequest(
    
    @Schema(description = "微信 openid", example = "rider_dev_xxx")
    String openid,
    
    @NotBlank(message = "code 不能为空")
    @Schema(description = "微信 code", example = "xxx")
    String code
) {
}
