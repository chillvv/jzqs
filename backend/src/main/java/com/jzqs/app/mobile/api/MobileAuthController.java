package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.mobile.MobileAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {
    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @GetMapping("/verify")
    public ApiResponse<MobileTokenVerifyResponse> verify(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(mobileAuthService.verify(extractToken(authorization)));
    }

    @PostMapping("/wx-login")
    public ApiResponse<MobileAuthStateResponse> wxLogin(@Valid @RequestBody MobileWxLoginRequest request) {
        return ApiResponse.success(mobileAuthService.wxLogin(request.code()));
    }

    @PostMapping("/logout")
    public ApiResponse<MobileLogoutResponse> logout(@RequestHeader("Authorization") String authorization) {
        mobileAuthService.logout(extractToken(authorization));
        return ApiResponse.success(new MobileLogoutResponse("退出登录成功"));
    }

    @PostMapping("/phone-login")
    public ApiResponse<MobileAuthStateResponse> phoneLogin(@Valid @RequestBody MobilePhoneLoginRequest request) {
        return ApiResponse.success(mobileAuthService.phoneLogin(request.openid(), request.phone()));
    }

    @PostMapping("/register")
    public ApiResponse<MobileAuthStateResponse> register(@Valid @RequestBody MobileBindPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindPhone(request.openid(), request.phone(), request.nickname()));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<MobileAuthStateResponse> bindPhone(@Valid @RequestBody MobilePhoneCodeRequest request) {
        return ApiResponse.success(mobileAuthService.bindPhoneByCode(request.openid(), request.code()));
    }

    @PostMapping("/dev-phone")
    public ApiResponse<MobileAuthStateResponse> bindDevPhone(@Valid @RequestBody MobileDevPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindDevPhone(request.openid(), request.phone()));
    }

    @PostMapping("/complete-profile")
    public ApiResponse<MobileAuthStateResponse> completeProfile(@Valid @RequestBody MobileCompleteProfileRequest request) {
        return ApiResponse.success(mobileAuthService.completeProfile(request.openid(), request.nickname()));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("无效的 Authorization header");
        }
        return authorization.substring(7);
    }
}
