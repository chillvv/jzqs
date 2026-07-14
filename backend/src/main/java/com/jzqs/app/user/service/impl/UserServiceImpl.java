package com.jzqs.app.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
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
        LocalDateTime now = LocalDateTime.now();
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setPhone(request.phone().trim());
        entity.setRole(request.role().trim());
        entity.setStatus("ENABLED");
        entity.setPasswordHash("{noop}init123456");
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
        existing.setPhone(request.phone().trim());
        existing.setRole(request.role().trim());
        existing.setStatus(request.status().trim());
        existing.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(existing);
        return detail(userId);
    }

    private void assertUsernameUnique(String username, Long excludeId) {
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
