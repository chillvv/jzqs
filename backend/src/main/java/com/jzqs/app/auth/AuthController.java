package com.jzqs.app.auth;

import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest body) {
        return ApiResponse.success(authService.login(body.code(), body.userType()));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<AuthBindPhoneResponse> bindPhone(@Valid @RequestBody AuthBindPhoneRequest body) {
        return ApiResponse.success(authService.bindPhone(body.code(), body.userType()));
    }

    @PostMapping("/register-phone")
    public ApiResponse<AuthBindPhoneResponse> registerPhone(@Valid @RequestBody AuthRegisterPhoneRequest body) {
        return ApiResponse.success(authService.registerPhone(body.phone(), body.nickname(), body.openid(), body.userType()));
    }

    @PostMapping("/phone-login")
    public ApiResponse<AuthBindPhoneResponse> phoneLogin(@Valid @RequestBody AuthPhoneLoginRequest body) {
        return ApiResponse.success(authService.loginByPhone(body.phone(), body.openid(), body.userType()));
    }

    @GetMapping("/verify")
    public ApiResponse<AuthVerifyResponse> verify(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(authService.verify(extractToken(authorization)));
    }

    @PostMapping("/logout")
    public ApiResponse<AuthLogoutResponse> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(extractToken(authorization));
        return ApiResponse.success(new AuthLogoutResponse("退出登录成功"));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("无效的 Authorization header");
        }
        return authorization.substring(7);
    }
}

record AuthLoginRequest(
    @NotBlank(message = "code 不能为空") String code,
    @NotBlank(message = "userType 不能为空") String userType
) {
}

record AuthBindPhoneRequest(
    @NotBlank(message = "code 不能为空") String code,
    @NotBlank(message = "userType 不能为空") String userType
) {
}

record AuthRegisterPhoneRequest(
    @NotBlank(message = "phone 不能为空") String phone,
    @NotBlank(message = "nickname 不能为空") String nickname,
    String openid,
    @NotBlank(message = "userType 不能为空") String userType
) {
}

record AuthPhoneLoginRequest(
    @NotBlank(message = "phone 不能为空") String phone,
    String openid,
    @NotBlank(message = "userType 不能为空") String userType
) {
}
