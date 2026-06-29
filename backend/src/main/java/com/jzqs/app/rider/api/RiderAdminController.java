package com.jzqs.app.rider.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.rider.model.dto.RiderCreateRequest;
import com.jzqs.app.rider.model.dto.RiderQueryRequest;
import com.jzqs.app.rider.model.dto.RiderUpdateRequest;
import com.jzqs.app.rider.model.vo.RiderDetailResponse;
import com.jzqs.app.rider.model.vo.RiderItemResponse;
import com.jzqs.app.rider.service.RiderAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/riders")
public class RiderAdminController {
    private final RiderAdminService riderAdminService;

    public RiderAdminController(RiderAdminService riderAdminService) {
        this.riderAdminService = riderAdminService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RiderItemResponse>> page(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String authStatus,
        @RequestParam(required = false) String employmentStatus
    ) {
        return ApiResponse.success(riderAdminService.page(
            new RiderQueryRequest(page, size, keyword, authStatus, employmentStatus)
        ));
    }

    @GetMapping("/{riderId}")
    public ApiResponse<RiderDetailResponse> detail(@PathVariable Long riderId) {
        return ApiResponse.success(riderAdminService.detail(riderId));
    }

    @PostMapping
    public ApiResponse<RiderDetailResponse> create(@Valid @RequestBody RiderCreateRequest request) {
        return ApiResponse.success(riderAdminService.create(request));
    }

    @PutMapping("/{riderId}")
    public ApiResponse<RiderDetailResponse> update(
        @PathVariable Long riderId,
        @Valid @RequestBody RiderUpdateRequest request
    ) {
        return ApiResponse.success(riderAdminService.update(riderId, request));
    }

    @DeleteMapping("/{riderId}")
    public ApiResponse<Void> delete(@PathVariable Long riderId) {
        riderAdminService.delete(riderId);
        return ApiResponse.success(null);
    }
}
