package com.jzqs.app.packageplan.api;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.packageplan.service.PackageGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/admin/package-grants")
public class PackageGrantController {
    private final PackageGrantService packageGrantService;

    public PackageGrantController(PackageGrantService packageGrantService) {
        this.packageGrantService = packageGrantService;
    }

    @PostMapping
    @RateLimit(key = "admin:package-grants:create", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:package-grants:create", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "PACKAGE_PLAN", action = "GRANT")
    public ApiResponse<GrantPackageResponse> grant(@Valid @RequestBody GrantPackageRequest request) {
        return ApiResponse.success(packageGrantService.grantPackage(
            request.customerId(),
            request.packageCode(),
            request.totalMeals(),
            AdminRequestContextSupport.requireOperatorName()
        ));
    }
}
