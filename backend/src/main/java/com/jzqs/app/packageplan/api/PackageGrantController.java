package com.jzqs.app.packageplan.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.packageplan.service.PackageGrantService;
import jakarta.validation.Valid;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> grant(@Valid @RequestBody GrantPackageRequest request) {
        return ApiResponse.success(packageGrantService.grantPackage(
            request.customerId(),
            request.packageCode(),
            request.totalMeals(),
            request.operatorName()
        ));
    }
}
