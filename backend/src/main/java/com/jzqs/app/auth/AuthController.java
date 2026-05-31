package com.jzqs.app.auth;

import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 统一认证 Controller
 * 提供微信小程序登录、绑定手机号、验证 token 等功能
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 微信静默登录
     * 通过 wx.login() 获取的 code 换取 openid，返回登录状态
     */
    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponse.success(authService.login(request.code(), request.userType()));
    }

    /**
     * 绑定手机号（微信一键登录）
     * 使用 getPhoneNumber 返回的动态令牌换取手机号并绑定
     */
    @PostMapping("/bind-phone")
    public ApiResponse<AuthBindPhoneResponse> bindPhone(@Valid @RequestBody AuthBindPhoneRequest request) {
        return ApiResponse.success(authService.bindPhone(request.code(), request.userType()));
    }

    /**
     * 验证 token 有效性
     * 用于冷启动时检查本地 token 是否仍然有效
     */
    @GetMapping("/verify")
    public ApiResponse<AuthVerifyResponse> verify(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        return ApiResponse.success(authService.verify(token));
    }

    /**
     * 手动输入手机号注册/登录
     * 用户手动输入手机号和姓名完成注册
     */
    @PostMapping("/register-phone")
    public ApiResponse<AuthBindPhoneResponse> registerPhone(@Valid @RequestBody AuthRegisterPhoneRequest request) {
        return ApiResponse.success(authService.registerPhone(request.phone(), request.nickname(), request.openid(), request.userType()));
    }

    /**
     * 老用户手机号登录
     * 顾客和骑手都通过统一入口按 userType 登录
     */
    @PostMapping("/phone-login")
    public ApiResponse<AuthBindPhoneResponse> phoneLogin(@Valid @RequestBody AuthPhoneLoginRequest request) {
        return ApiResponse.success(authService.loginByPhone(request.phone(), request.openid(), request.userType()));
    }

    /**
     * 老用户手机号登录
     * 已有顾客账号仅通过手机号登录，不在登录时修改姓名
     */
    @PostMapping("/customer-phone-login")
    public ApiResponse<AuthBindPhoneResponse> customerPhoneLogin(@Valid @RequestBody AuthCustomerPhoneLoginRequest request) {
        return ApiResponse.success(authService.loginCustomerByPhone(request.phone(), request.openid()));
    }

    /**
     * 退出登录
     * 前端清除本地 token 即可，后端可选实现黑名单
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        authService.logout(token);
        return ApiResponse.success(Map.of("message", "退出登录成功"));
    }

    /**
     * 从 Authorization header 中提取 token
     */
    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("无效的 Authorization header");
        }
        return authorization.substring(7);
    }
}

/**
 * 登录请求
 */
record AuthLoginRequest(
        @NotBlank(message = "code 不能为空") String code,
        @NotBlank(message = "userType 不能为空") String userType
) {}

/**
 * 登录响应
 */
record AuthLoginResponse(
        String token,
        Long userId,
        String userType,
        boolean registered,
        String openid
) {}

/**
 * 绑定手机号请求
 */
record AuthBindPhoneRequest(
        @NotBlank(message = "code 不能为空") String code,
        @NotBlank(message = "userType 不能为空") String userType
) {}

/**
 * 绑定手机号响应
 */
record AuthBindPhoneResponse(
        String token,
        Long userId,
        String userType,
        String phone,
        String riderName,
        String riderStatus,
        Boolean workbenchEnabled
) {}

/**
 * 手动输入手机号注册请求
 */
record AuthRegisterPhoneRequest(
        @NotBlank(message = "phone 不能为空") String phone,
        @NotBlank(message = "nickname 不能为空") String nickname,
        String openid,
        @NotBlank(message = "userType 不能为空") String userType
) {}

/**
 * 顾客手机号登录请求
 */
record AuthCustomerPhoneLoginRequest(
        @NotBlank(message = "phone 不能为空") String phone,
        String openid
) {}

/**
 * 统一手机号登录请求
 */
record AuthPhoneLoginRequest(
        @NotBlank(message = "phone 不能为空") String phone,
        String openid,
        @NotBlank(message = "userType 不能为空") String userType
) {}

/**
 * 验证响应
 */
record AuthVerifyResponse(
        boolean valid,
        Long userId,
        String userType
) {}
