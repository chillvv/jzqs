package com.jzqs.app.rider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.service.DispatchService;
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
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiderAdminServiceImpl implements RiderAdminService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RiderMapper riderMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DispatchService dispatchService;

    public RiderAdminServiceImpl(RiderMapper riderMapper, JdbcTemplate jdbcTemplate, DispatchService dispatchService) {
        this.riderMapper = riderMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchService = dispatchService;
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
        assertRiderPhoneUnique(request.phone(), null);
        LocalDateTime now = LocalDateTime.now();

        String areaCode = request.areaCode() == null || request.areaCode().isBlank() ? null : request.areaCode().trim();
        RiderEntity entity = new RiderEntity();
        entity.setRiderName(request.riderName().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setPhone(request.phone().trim());
        entity.setEmploymentStatus(request.employmentStatus() != null ? request.employmentStatus().trim() : "ACTIVE");
        entity.setAuthStatus("ACTIVE");
        entity.setDefaultAreaCode(areaCode);
        entity.setRemark(request.remark() != null ? request.remark().trim() : null);
        entity.setDisplayOrder(0);
        entity.setAssignedBy("admin");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        riderMapper.insert(entity);
        // 双向同步：设置骑手负责区域时，同步到区域默认骑手绑定，
        // 避免「骑手档案有区域、派单系统不认」的不一致（与调度模块保持一致）。
        if (areaCode != null && "ACTIVE".equalsIgnoreCase(entity.getAuthStatus())) {
            dispatchService.updateAreaBinding(areaCode, null, entity.getId(), null, "admin");
        }
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
        String newPhone = request.phone().trim();
        if (!newPhone.equals(existing.getPhone())) {
            assertRiderPhoneUnique(newPhone, riderId);
        }

        String newAreaCode = request.areaCode() == null || request.areaCode().isBlank() ? null : request.areaCode().trim();
        String oldAreaCode = existing.getDefaultAreaCode();

        existing.setRiderName(newRiderName);
        existing.setDisplayName(request.displayName().trim());
        existing.setPhone(newPhone);
        existing.setEmploymentStatus(request.employmentStatus() != null ? request.employmentStatus().trim() : existing.getEmploymentStatus());
        existing.setAuthStatus(request.authStatus() != null ? request.authStatus().trim() : existing.getAuthStatus());
        existing.setDefaultAreaCode(newAreaCode);
        existing.setRemark(request.remark() != null ? request.remark().trim() : null);

        existing.setUpdatedAt(LocalDateTime.now());
        riderMapper.updateById(existing);
        // 双向同步：负责区域变化时，释放旧区域绑定、迁移未完成订单并绑定新区域。
        if (!Objects.equals(oldAreaCode, newAreaCode)) {
            jdbcTemplate.update(
                "UPDATE dispatch_area_bindings SET default_rider_profile_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE default_rider_profile_id = ?",
                riderId
            );
            if (newAreaCode != null) {
                jdbcTemplate.update(
                    "UPDATE dispatch_assignments SET area_code = ? WHERE rider_profile_id = ? AND status IN ('PENDING', 'AREA_ASSIGNED', 'DISPATCHING')",
                    newAreaCode, riderId
                );
                dispatchService.updateAreaBinding(newAreaCode, null, riderId, null, "admin");
            }
        }
        return detail(riderId);
    }

    @Override
    @Transactional
    public void delete(Long riderId) {
        RiderEntity existing = riderMapper.selectById(riderId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RIDER_NOT_FOUND, "骑手不存在");
        }
        // 彻底清理：同名骑手可能跨餐期存在多条档案（历史迁移导致），必须全部一起删除，
        // 否则会出现"删了一条、同名还在"的残留问题。
        List<Long> sameNameIds = jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE rider_name = ?",
            (rs, rn) -> rs.getLong("id"),
            existing.getRiderName()
        );
        // 删除前先解绑所有外键引用，避免外键约束导致删除失败（原表现为"系统冲突/409"）。
        String riderName = existing.getRiderName();
        for (Long id : sameNameIds) {
            // 1) 该骑手名下的未完成派单退回待分配：清空骑手关联与姓名，状态置回 PENDING，
            //    让订单回到起手池可重新分配。骑手已删除，进度/区域管理不再显示归属于他。
            //    用 (rider_profile_id = ? OR rider_name = ?) 同时覆盖"按ID关联"和"仅留姓名"的历史派单。
            //    注意：必须排除已送达(DELIVERED)的派单，否则会把已完成订单打回 PENDING，
            //    制造"订单已送达但派单仍待分配"的僵尸数据，后续绑骑手时会报 409。
            jdbcTemplate.update(
                "UPDATE dispatch_assignments SET rider_profile_id = NULL, rider_name = NULL, status = 'PENDING' WHERE (rider_profile_id = ? OR rider_name = ?) AND status IN ('PENDING', 'AREA_ASSIGNED', 'DISPATCHING')",
                id, riderName);
            // 2) 解除该骑手作为区域默认/备用骑手的绑定。
            jdbcTemplate.update(
                "UPDATE dispatch_area_bindings SET default_rider_profile_id = NULL WHERE default_rider_profile_id = ?", id);
            jdbcTemplate.update(
                "UPDATE dispatch_area_bindings SET backup_rider_profile_id = NULL WHERE backup_rider_profile_id = ?", id);
        }
        // 3) 删除同名的所有餐期档案，彻底清理残留。
        for (Long id : sameNameIds) {
            riderMapper.deleteById(id);
        }
    }

    private void assertRiderNameUnique(String riderName, Long excludeId) {
        // 骑手唯一：姓名全局唯一（已去除餐期拆分）。
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

    private void assertRiderPhoneUnique(String phone, Long excludeId) {
        LambdaQueryWrapper<RiderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiderEntity::getPhone, phone.trim());
        if (excludeId != null) {
            wrapper.ne(RiderEntity::getId, excludeId);
        }
        Long count = riderMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号已存在");
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
