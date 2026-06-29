package com.jzqs.app.mobile.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 骑手登录响应
 */
@Schema(description = "骑手登录响应")
public record RiderLoginResponse(
    
    @Schema(description = "是否登录成功")
    boolean success,
    
    @Schema(description = "认证令牌")
    String token,
    
    @Schema(description = "骑手ID")
    long riderId,
    
    @Schema(description = "骑手姓名")
    String name,
    
    @Schema(description = "手机号")
    String phone,
    
    @Schema(description = "骑手状态：active-已激活, pending-待审核")
    String status,
    
    @Schema(description = "提示信息")
    String message
) {
}
