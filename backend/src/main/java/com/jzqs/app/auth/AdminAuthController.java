package com.jzqs.app.auth;

import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminAuthLoginResponse> login(@Valid @RequestBody AdminAuthLoginRequest body) {
        return ApiResponse.success(adminAuthService.login(body.phone(), body.password()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminAuthProfileResponse> me(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(adminAuthService.me(extractToken(authorization)));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, Object>> changePassword(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody AdminChangePasswordRequest body
    ) {
        return ApiResponse.success(adminAuthService.changePassword(extractToken(authorization), body.oldPassword(), body.newPassword()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(adminAuthService.logout(extractToken(authorization)));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("无效的 Authorization header");
        }
        return authorization.substring(7);
    }
}

record AdminAuthLoginRequest(
    @NotBlank(message = "phone 不能为空") String phone,
    @NotBlank(message = "password 不能为空") String password
) {
}

record AdminAuthLoginResponse(
    String token,
    Long userId,
    String displayName,
    String phone,
    String role
) {
}

record AdminAuthProfileResponse(
    Long userId,
    String displayName,
    String phone,
    String role
) {
}

record AdminChangePasswordRequest(
    @NotBlank(message = "oldPassword 不能为空") String oldPassword,
    @NotBlank(message = "newPassword 不能为空") String newPassword
) {
}
