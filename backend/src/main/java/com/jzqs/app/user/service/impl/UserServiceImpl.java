package com.jzqs.app.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.security.AdminRequestContext;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.common.util.PasswordUtils;
import com.jzqs.app.user.mapper.UserMapper;
import com.jzqs.app.user.model.dto.UserCreateRequest;
import com.jzqs.app.user.model.dto.UserQueryRequest;
import com.jzqs.app.user.model.dto.UserUpdateRequest;
import com.jzqs.app.user.model.entity.UserEntity;
import com.jzqs.app.user.model.vo.UserDetailResponse;
import com.jzqs.app.user.model.vo.UserItemResponse;
import com.jzqs.app.user.service.UserService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PageResponse<UserItemResponse> page(UserQueryRequest queryRequest) {
        Page<UserEntity> page = new Page<>(queryRequest.pageNoOrDefault(), queryRequest.pageSizeOrDefault());
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(queryRequest.keyword())) {
            String keyword = queryRequest.keyword().trim();
            wrapper.and(w -> w
                .like(UserEntity::getUsername, keyword)
                .or()
                .like(UserEntity::getDisplayName, keyword)
                .or()
                .like(UserEntity::getPhone, keyword)
            );
        }
        if (notBlank(queryRequest.status())) {
            wrapper.eq(UserEntity::getStatus, queryRequest.status().trim());
        }
        wrapper.orderByDesc(UserEntity::getId);

        Page<UserEntity> result = userMapper.selectPage(page, wrapper);
        List<UserItemResponse> items = result.getRecords().stream()
            .map(this::toItem)
            .toList();
        return PageResponse.of(items, (int) result.getCurrent(), (int) result.getSize(), (int) result.getTotal());
    }

    @Override
    public UserDetailResponse detail(Long userId) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return toDetail(entity);
    }

    @Override
    @Transactional
    public UserDetailResponse create(UserCreateRequest request) {
        assertUsernameUnique(request.username(), null);
        assertPhoneUnique(request.phone(), null);
        String role = normalizeRole(request.role());
        LocalDateTime now = LocalDateTime.now();
        UserEntity entity = new UserEntity();
        // 用户名未单独提供时以手机号作为用户名（登录统一使用手机号+密码）
        entity.setUsername(request.username() == null || request.username().isBlank()
            ? request.phone().trim()
            : request.username().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setPhone(request.phone().trim());
        entity.setRole(role);
        entity.setStatus("ENABLED");
        // 密码以手机号为盐做 SHA-256 存储，创建后即可用手机号+密码登录
        entity.setPasswordHash(PasswordUtils.hash(request.password(), request.phone().trim()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);
        return detail(entity.getId());
    }

    @Override
    @Transactional
    public UserDetailResponse update(Long userId, UserUpdateRequest request) {
        UserEntity existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        existing.setDisplayName(request.displayName().trim());
        existing.setRole(normalizeRole(request.role()));
        existing.setStatus(normalizeStatus(request.status()));
        existing.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(existing);
        return detail(userId);
    }

    @Override
    @Transactional
    public UserDetailResponse resetPassword(Long userId, String newPassword) {
        UserEntity existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        existing.setPasswordHash(PasswordUtils.hash(newPassword, existing.getPhone()));
        existing.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(existing);
        return detail(userId);
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        UserEntity existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        // 禁止删除自己，避免管理员误删当前登录账号导致无法管理
        AdminRequestContext admin = AdminRequestContextSupport.currentAdminOrNull();
        if (admin != null && admin.userId() != null && admin.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能删除当前登录的账号");
        }
        // 禁止删除最后一个老板账号，避免系统失去最高权限
        if ("OWNER".equals(existing.getRole())) {
            LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserEntity::getRole, "OWNER");
            Long ownerCount = userMapper.selectCount(wrapper);
            if (ownerCount != null && ownerCount <= 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能删除唯一的老板账号");
            }
        }
        userMapper.deleteById(userId);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!normalized.equals("OWNER") && !normalized.equals("ADMIN") && !normalized.equals("OPERATOR")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色必须是 OWNER/ADMIN/OPERATOR");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("ENABLED") && !normalized.equals("DISABLED")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态必须是 ENABLED/DISABLED");
        }
        return normalized;
    }

    private void assertPhoneUnique(String phone, Long excludeId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getPhone, phone.trim());
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该手机号已被使用");
        }
    }

    private void assertUsernameUnique(String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            return;
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username.trim());
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在");
        }
    }

    private UserItemResponse toItem(UserEntity entity) {
        return new UserItemResponse(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getPhone(),
            entity.getRole(),
            entity.getStatus()
        );
    }

    private UserDetailResponse toDetail(UserEntity entity) {
        return new UserDetailResponse(
            entity.getId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getPhone(),
            entity.getRole(),
            entity.getStatus(),
            formatDateTime(entity.getCreatedAt()),
            formatDateTime(entity.getUpdatedAt())
        );
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATETIME_FORMATTER.format(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
