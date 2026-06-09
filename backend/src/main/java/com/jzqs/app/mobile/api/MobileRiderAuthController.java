package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.mobile.MobileAuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/rider-auth")
public class MobileRiderAuthController {
    private final MobileAuthService mobileAuthService;

    public MobileRiderAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<Map<String, Object>> wxLogin(@Valid @RequestBody RiderWxLoginRequest request) {
        return ApiResponse.success(mobileAuthService.riderWxLogin(request.code()));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<Map<String, Object>> bindPhone(@Valid @RequestBody RiderBindPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindRiderPhone(request.openid(), request.phone(), request.nickname()));
    }

    @GetMapping("/me")
    public ApiResponse<RiderAuthProfileResponse> me(@RequestParam String riderName) {
        return ApiResponse.success(mobileAuthService.riderProfile(riderName));
    }

    @PostMapping("/verify-token")
    public ApiResponse<Map<String, Object>> verifyToken(@Valid @RequestBody RiderTokenVerifyRequest request) {
        return ApiResponse.success(mobileAuthService.verifyRiderToken(request.token()));
    }
}
