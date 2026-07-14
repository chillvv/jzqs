package com.jzqs.app.user.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.user.model.dto.UserCreateRequest;
import com.jzqs.app.user.model.dto.UserQueryRequest;
import com.jzqs.app.user.model.dto.UserUpdateRequest;
import com.jzqs.app.user.model.vo.UserDetailResponse;
import com.jzqs.app.user.model.vo.UserItemResponse;
import com.jzqs.app.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserItemResponse>> page(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(userService.page(new UserQueryRequest(page, size, keyword, status)));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserDetailResponse> detail(@PathVariable Long userId) {
        return ApiResponse.success(userService.detail(userId));
    }

    @PostMapping
    public ApiResponse<UserDetailResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserDetailResponse> update(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.update(userId, request));
    }
}
