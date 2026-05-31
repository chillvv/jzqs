package com.jzqs.app.packageplan.service.impl;

import com.jzqs.app.packageplan.service.PackageGrantService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PackageGrantServiceImpl implements PackageGrantService {
    private final JdbcTemplate jdbcTemplate;

    public PackageGrantServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Map<String, Object> grantPackage(long customerId, String packageCode, int totalMeals, String operatorName) {
        Long packagePlanId = jdbcTemplate.queryForObject("SELECT id FROM package_plans WHERE package_code = ?", Long.class, packageCode);
        Long existingWalletId = jdbcTemplate.query(
            "SELECT id FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (existingWalletId == null) {
            existingWalletId = insertAndReturnId(
                "INSERT INTO meal_wallets (customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, ?, 0, 0, TRUE)",
                customerId, packagePlanId, totalMeals
            );
        } else {
            jdbcTemplate.update(
                "UPDATE meal_wallets SET package_plan_id = ?, total_meals = ?, reserved_meals = 0, consumed_meals = 0 WHERE id = ?",
                packagePlanId,
                totalMeals,
                existingWalletId
            );
        }
        insertWalletTransaction(existingWalletId, "OPEN", totalMeals, operatorName, "后台开卡");
        return Map.of("customerId", customerId, "packageCode", packageCode, "remainingMeals", totalMeals);
    }

    private void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transactions (wallet_id, transaction_type, meal_delta, operator_name, remark, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
            walletId,
            transactionType,
            mealDelta,
            operatorName,
            remark
        );
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idValue = keys.containsKey("ID") ? keys.get("ID") : keys.get("id");
            if (idValue == null) {
                idValue = keys.values().iterator().next();
            }
            if (idValue instanceof Number number) {
                return number.longValue();
            }
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        return 0L;
    }
}
