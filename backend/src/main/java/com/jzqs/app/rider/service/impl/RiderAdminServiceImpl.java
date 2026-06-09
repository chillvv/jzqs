package com.jzqs.app.rider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.rider.mapper.RiderMapper;
import com.jzqs.app.rider.model.dto.RiderCreateRequest;
import com.jzqs.app.rider.model.dto.RiderQueryRequest;
import com.jzqs.app.rider.model.dto.RiderUpdateRequest;
import com.jzqs.app.rider.model.entity.RiderEntity;
import com.jzqs.app.rider.model.vo.RiderDetailResponse;
import com.jzqs.app.rider.model.vo.RiderItemResponse;
import com.jzqs.app.rider.service.RiderAdminService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiderAdminServiceImpl implements RiderAdminService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RiderMapper riderMapper;

    public RiderAdminServiceImpl(RiderMapper riderMapper) {
        this.riderMapper = riderMapper;
    }

    @Override
    public PageResponse<RiderItemResponse> page(RiderQueryRequest queryRequest) {
        Page<RiderEntity> page = new Page<>(queryRequest.pageNoOrDefault(), queryRequest.pageSizeOrDefault());
        LambdaQueryWrapper<RiderEntity> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(queryRequest.keyword())) {
            String keyword = queryRequest.keyword().trim();
            wrapper.and(w -> w
                .like(RiderEntity::getRiderName, keyword)
                .or()
                .like(RiderEntity::getDisplayName, keyword)
                .or()
                .like(RiderEntity::getPhone, keyword)
            );
        }
        if (notBlank(queryRequest.authStatus())) {
            wrapper.eq(RiderEntity::getAuthStatus, queryRequest.authStatus().trim());
        }
        if (notBlank(queryRequest.employmentStatus())) {
            wrapper.eq(RiderEntity::getEmploymentStatus, queryRequest.employmentStatus().trim());
        }
        wrapper.orderByDesc(RiderEntity::getId);

        Page<RiderEntity> result = riderMapper.selectPage(page, wrapper);
        List<RiderItemResponse> items = result.getRecords().stream()
            .map(this::toItem)
            .toList();
        return PageResponse.of(items, (int) result.getCurrent(), (int) result.getSize(), (int) result.getTotal());
    }

    @Override
    public RiderDetailResponse detail(Long riderId) {
        RiderEntity entity = riderMapper.selectById(riderId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RIDER_NOT_FOUND, "骑手不存在");
        }
        return toDetail(entity);
    }

    @Override
    @Transactional
    public RiderDetailResponse create(RiderCreateRequest request) {
        assertRiderNameUnique(request.riderName(), null);
        LocalDateTime now = LocalDateTime.now();

        RiderEntity entity = new RiderEntity();
        entity.setRiderName(request.riderName().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setPhone(request.phone().trim());
        entity.setEmploymentStatus(request.employmentStatus() != null ? request.employmentStatus().trim() : "ACTIVE");
        entity.setAuthStatus("ACTIVE");
        entity.setDefaultAreaCode(request.areaCode() != null ? request.areaCode().trim() : null);
        entity.setRemark(request.remark() != null ? request.remark().trim() : null);
        entity.setDisplayOrder(0);
        entity.setAssignedBy("admin");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        riderMapper.insert(entity);
        return detail(entity.getId());
    }

    @Override
    @Transactional
    public RiderDetailResponse update(Long riderId, RiderUpdateRequest request) {
        RiderEntity existing = riderMapper.selectById(riderId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RIDER_NOT_FOUND, "骑手不存在");
        }

        String newRiderName = request.riderName().trim();
        if (!newRiderName.equals(existing.getRiderName())) {
            assertRiderNameUnique(newRiderName, riderId);
        }

        existing.setRiderName(newRiderName);
        existing.setDisplayName(request.displayName().trim());
        existing.setPhone(request.phone().trim());
        existing.setEmploymentStatus(request.employmentStatus() != null ? request.employmentStatus().trim() : existing.getEmploymentStatus());
        existing.setAuthStatus(request.authStatus() != null ? request.authStatus().trim() : existing.getAuthStatus());
        existing.setDefaultAreaCode(request.areaCode() != null ? request.areaCode().trim() : null);
        existing.setRemark(request.remark() != null ? request.remark().trim() : null);

        existing.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateById(existing);
        return detail(riderId);
    }

    @Override
    @Transactional
    public void delete(Long riderId) {
        RiderEntity existing = riderMapper.selectById(riderId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RIDER_NOT_FOUND, "骑手不存在");
        }
        riderMapper.deleteById(riderId);
    }

    private void assertRiderNameUnique(String riderName, Long excludeId) {
        LambdaQueryWrapper<RiderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiderEntity::getRiderName, riderName.trim());
        if (excludeId != null) {
            wrapper.ne(RiderEntity::getId, excludeId);
        }
        Long count = riderMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "骑手名称已存在");
        }
    }

    private RiderItemResponse toItem(RiderEntity entity) {
        return new RiderItemResponse(
            entity.getId(),
            entity.getRiderName(),
            entity.getDisplayName(),
            entity.getPhone(),
            entity.getAuthStatus(),
            entity.getEmploymentStatus(),
            entity.getDefaultAreaCode(),
            entity.getAssignedBy()
        );
    }

    private RiderDetailResponse toDetail(RiderEntity entity) {
        return new RiderDetailResponse(
            entity.getId(),
            entity.getRiderName(),
            entity.getDisplayName(),
            entity.getPhone(),
            entity.getAuthStatus(),
            entity.getEmploymentStatus(),
            entity.getDefaultAreaCode(),
            entity.getRemark(),
            entity.getAssignedBy(),
            formatDateTime(entity.getCreatedAt()),
            formatDateTime(entity.getUpdatedAt()),
            formatDateTime(entity.getFirstLoginAt()),
            formatDateTime(entity.getLastLoginAt())
        );
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) return "";
        return DATETIME_FORMATTER.format(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
