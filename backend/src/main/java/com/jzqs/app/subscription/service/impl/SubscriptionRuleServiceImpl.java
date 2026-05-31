package com.jzqs.app.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.subscription.api.SubscriptionRuleRequest;
import com.jzqs.app.subscription.api.SubscriptionRuleResponse;
import com.jzqs.app.subscription.mapper.SubscriptionRuleMapper;
import com.jzqs.app.subscription.model.entity.SubscriptionRuleEntity;
import com.jzqs.app.subscription.service.SubscriptionRuleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionRuleServiceImpl implements SubscriptionRuleService {
    private final SubscriptionRuleMapper subscriptionRuleMapper;
    private final JdbcTemplate jdbcTemplate;

    public SubscriptionRuleServiceImpl(SubscriptionRuleMapper subscriptionRuleMapper, JdbcTemplate jdbcTemplate) {
        this.subscriptionRuleMapper = subscriptionRuleMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SubscriptionRuleResponse> listRules(String keyword, String status) {
        String sql = """
            SELECT
                sr.id,
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                sr.start_date,
                sr.end_date,
                sr.lunch_enabled,
                sr.lunch_quantity,
                sr.dinner_enabled,
                sr.dinner_quantity,
                sr.default_address_id,
                COALESCE(ca.address_line, '') AS default_address,
                COALESCE(sr.default_note, '') AS default_note,
                sr.is_priority_follow,
                sr.paused,
                sr.active,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                sr.created_at,
                sr.updated_at
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            WHERE 1=1
            """;

        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql += " AND (c.name LIKE ? OR c.phone LIKE ?)";
            String likePattern = "%" + keyword.trim() + "%";
            params.add(likePattern);
            params.add(likePattern);
        }

        if (status != null && !status.equals("ALL")) {
            LocalDate today = LocalDate.now();
            switch (status) {
                case "ACTIVE" -> sql += " AND sr.active = TRUE AND sr.paused = FALSE AND sr.start_date <= ? AND sr.end_date >= ?";
                case "PAUSED" -> sql += " AND sr.active = TRUE AND sr.paused = TRUE";
                case "EXPIRED" -> sql += " AND sr.active = TRUE AND sr.end_date < ?";
                case "INACTIVE" -> sql += " AND sr.active = FALSE";
            }
            if (status.equals("ACTIVE")) {
                params.add(today);
                params.add(today);
            } else if (status.equals("EXPIRED")) {
                params.add(today);
            }
        }

        sql += " ORDER BY sr.id DESC";

        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
            LocalDate startDate = rs.getDate("start_date").toLocalDate();
            LocalDate endDate = rs.getDate("end_date").toLocalDate();
            boolean active = rs.getBoolean("active");
            boolean paused = rs.getBoolean("paused");
            String calculatedStatus = calculateStatus(active, paused, startDate, endDate, LocalDate.now());

            return new SubscriptionRuleResponse(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                startDate,
                endDate,
                rs.getBoolean("lunch_enabled"),
                rs.getInt("lunch_quantity"),
                rs.getBoolean("dinner_enabled"),
                rs.getInt("dinner_quantity"),
                rs.getLong("default_address_id") == 0 ? null : rs.getLong("default_address_id"),
                rs.getString("default_address"),
                rs.getString("default_note"),
                rs.getBoolean("is_priority_follow"),
                paused,
                active,
                calculatedStatus,
                rs.getInt("remaining_meals"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        });
    }

    @Override
    @Transactional
    public SubscriptionRuleResponse createRule(SubscriptionRuleRequest request) {
        validateRule(request);

        SubscriptionRuleEntity entity = new SubscriptionRuleEntity();
        entity.setCustomerId(request.customerId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setLunchEnabled(request.lunchEnabled());
        entity.setLunchQuantity(request.lunchQuantity());
        entity.setDinnerEnabled(request.dinnerEnabled());
        entity.setDinnerQuantity(request.dinnerQuantity());
        entity.setDefaultAddressId(request.defaultAddressId());
        entity.setDefaultNote(request.defaultNote());
        entity.setIsPriorityFollow(request.isPriorityFollow());
        entity.setActive(true);
        entity.setPaused(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        subscriptionRuleMapper.insert(entity);

        return getRuleById(entity.getId());
    }

    @Override
    @Transactional
    public SubscriptionRuleResponse updateRule(long id, SubscriptionRuleRequest request) {
        validateRule(request);

        SubscriptionRuleEntity entity = subscriptionRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_RULE_NOT_FOUND, "固定订餐计划不存在");
        }

        entity.setCustomerId(request.customerId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setLunchEnabled(request.lunchEnabled());
        entity.setLunchQuantity(request.lunchQuantity());
        entity.setDinnerEnabled(request.dinnerEnabled());
        entity.setDinnerQuantity(request.dinnerQuantity());
        entity.setDefaultAddressId(request.defaultAddressId());
        entity.setDefaultNote(request.defaultNote());
        entity.setIsPriorityFollow(request.isPriorityFollow());
        entity.setUpdatedAt(LocalDateTime.now());

        subscriptionRuleMapper.updateById(entity);

        return getRuleById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteRule(long id) {
        SubscriptionRuleEntity entity = subscriptionRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_RULE_NOT_FOUND, "固定订餐计划不存在");
        }

        entity.setActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        subscriptionRuleMapper.updateById(entity);

        return Map.of("id", id, "status", "DELETED");
    }

    @Override
    @Transactional
    public Map<String, Object> togglePause(long id) {
        SubscriptionRuleEntity entity = subscriptionRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_RULE_NOT_FOUND, "固定订餐计划不存在");
        }

        entity.setPaused(!entity.getPaused());
        entity.setUpdatedAt(LocalDateTime.now());
        subscriptionRuleMapper.updateById(entity);

        return Map.of("id", id, "paused", entity.getPaused());
    }

    private void validateRule(SubscriptionRuleRequest request) {
        // 检查客户是否存在
        Integer customerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customers WHERE id = ?",
            Integer.class,
            request.customerId()
        );
        if (customerCount == null || customerCount == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在");
        }

        // 检查日期范围
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "开始日期不能晚于结束日期");
        }

        // 检查至少启用一个餐次
        if (!request.lunchEnabled() && !request.dinnerEnabled()) {
            throw new BusinessException(ErrorCode.NO_MEAL_ENABLED, "至少需要启用午餐或晚餐");
        }

        // 检查地址是否属于该客户
        if (request.defaultAddressId() != null) {
            Integer addressCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_addresses WHERE id = ? AND customer_id = ?",
                Integer.class,
                request.defaultAddressId(),
                request.customerId()
            );
            if (addressCount == null || addressCount == 0) {
                throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, "地址不存在或不属于该客户");
            }
        }
    }

    private SubscriptionRuleResponse getRuleById(long id) {
        List<SubscriptionRuleResponse> rules = listRules(null, null);
        return rules.stream()
            .filter(rule -> rule.id() == id)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_RULE_NOT_FOUND, "固定订餐计划不存在"));
    }

    private String calculateStatus(boolean active, boolean paused, LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (!active) return "INACTIVE";
        if (paused) return "PAUSED";
        if (today.isAfter(endDate)) return "EXPIRED";
        if (today.isBefore(startDate)) return "PENDING";
        return "ACTIVE";
    }
}
