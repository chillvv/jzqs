package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.mobile.MobileAuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {
    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<Map<String, Object>> wxLogin(@Valid @RequestBody MobileWxLoginRequest request) {
        return ApiResponse.success(mobileAuthService.wxLogin(request.code()));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<Map<String, Object>> bindPhone(@Valid @RequestBody MobileBindPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindPhone(request.openid(), request.phone(), request.nickname()));
    }

    @PostMapping("/dev-phone")
    public ApiResponse<Map<String, Object>> bindDevPhone(@Valid @RequestBody MobileDevPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindDevPhone(request.openid(), request.phone()));
    }

    @PostMapping("/complete-profile")
    public ApiResponse<Map<String, Object>> completeProfile(@Valid @RequestBody MobileCompleteProfileRequest request) {
        return ApiResponse.success(mobileAuthService.completeProfile(request.openid(), request.nickname()));
    }
}
