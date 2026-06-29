package com.jzqs.app.order.api;

import com.jzqs.app.common.api.ApiResponse;
import java.util.Collections;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class OrderPrepLegacyCompatibilityController {

    @GetMapping("/special-orders")
    public ApiResponse<List<LegacySpecialOrderResponse>> specialOrdersCompatibility() {
        // The special-order feature was removed. Cached admin bundles may still call this route,
        // so keep a no-op endpoint to avoid noisy 404s until all old assets are gone.
        return ApiResponse.success(Collections.emptyList());
    }
}
