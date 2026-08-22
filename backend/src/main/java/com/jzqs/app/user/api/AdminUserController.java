package com.jzqs.app.user.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.security.AdminRequestContext;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.user.model.dto.UserCreateRequest;
import com.jzqs.app.user.model.dto.UserQueryRequest;
import com.jzqs.app.user.model.dto.UserUpdateRequest;
import com.jzqs.app.user.model.vo.UserDetailResponse;
import com.jzqs.app.user.model.vo.UserItemResponse;
import com.jzqs.app.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @AuditAction(module = "ADMIN_USER", action = "CREATE")
    public ApiResponse<UserDetailResponse> create(@Valid @RequestBody UserCreateRequest request) {
        requireAccountManager();
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/{userId}")
    @AuditAction(module = "ADMIN_USER", action = "UPDATE")
    public ApiResponse<UserDetailResponse> update(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
        requireAccountManager();
        return ApiResponse.success(userService.update(userId, request));
    }

    @PostMapping("/{userId}/reset-password")
    @AuditAction(module = "ADMIN_USER", action = "RESET_PASSWORD")
    public ApiResponse<UserDetailResponse> resetPassword(
        @PathVariable Long userId,
        @Valid @RequestBody UserPasswordResetRequest request
    ) {
        requireAccountManager();
        return ApiResponse.success(userService.resetPassword(userId, request.newPassword()));
    }

    /**
     * 账号管理(创建/编辑/重置密码)仅限 OWNER/ADMIN，避免普通操作员接管他人账号
     */
    private void requireAccountManager() {
        AdminRequestContext admin = AdminRequestContextSupport.requireAdmin();
        String role = admin.role() == null ? "" : admin.role().trim().toUpperCase();
        if (!role.equals("OWNER") && !role.equals("ADMIN")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "仅老板或管理员可以管理后台账号");
        }
    }

    public record UserPasswordResetRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为6-32位")
        String newPassword
    ) {
    }
}
