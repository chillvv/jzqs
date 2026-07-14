package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.mobile.MobileAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/rider-auth")
public class MobileRiderAuthController {
    private final MobileAuthService mobileAuthService;

    public MobileRiderAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<RiderAuthStateResponse> wxLogin(@Valid @RequestBody RiderWxLoginRequest request) {
        return ApiResponse.success(mobileAuthService.riderWxLogin(request.code()));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<RiderAuthStateResponse> bindPhone(@Valid @RequestBody RiderBindPhoneRequest request) {
        return ApiResponse.success(mobileAuthService.bindRiderPhone(request.openid(), request.phone(), request.nickname()));
    }

    @GetMapping("/me")
    public ApiResponse<RiderAuthProfileResponse> me(
        @RequestAttribute(value = "riderName", required = false) String riderName,
        @RequestAttribute(value = "riderId", required = false) Long riderId
    ) {
        if (riderName != null && !riderName.isBlank()) {
            return ApiResponse.success(mobileAuthService.riderProfile(riderName));
        }
        return ApiResponse.success(mobileAuthService.riderProfile(riderId));
    }

    @PostMapping("/verify-token")
    public ApiResponse<RiderTokenVerifyResponse> verifyToken(@Valid @RequestBody RiderTokenVerifyRequest request) {
        return ApiResponse.success(mobileAuthService.verifyRiderToken(request.token()));
    }
}
