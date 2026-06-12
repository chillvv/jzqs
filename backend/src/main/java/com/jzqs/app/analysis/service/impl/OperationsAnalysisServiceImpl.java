package com.jzqs.app.analysis.service.impl;

import com.jzqs.app.analysis.api.AnalysisOverviewResponse;
import com.jzqs.app.analysis.api.CostEntryItem;
import com.jzqs.app.analysis.service.OperationsAnalysisService;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsAnalysisServiceImpl implements OperationsAnalysisService {
    private final JdbcTemplate jdbcTemplate;

    public OperationsAnalysisServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AnalysisOverviewResponse overview(String date) {
        LocalDate targetDate = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        BigDecimal totalCost = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM cost_entries WHERE cost_date = ?",
            BigDecimal.class,
            java.sql.Date.valueOf(targetDate)
        );
        Integer totalOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_orders WHERE serve_date = ?",
            Integer.class,
            java.sql.Date.valueOf(targetDate)
        );
        Integer totalMeals = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE do.serve_date = ?",
            Integer.class,
            java.sql.Date.valueOf(targetDate)
        );
        Integer specialOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE do.serve_date = ? AND (mso.user_note IS NOT NULL OR mso.merchant_remark IS NOT NULL OR mso.is_priority = TRUE)",
            Integer.class,
            java.sql.Date.valueOf(targetDate)
        );
        Integer aftersaleCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM aftersale_cases WHERE DATE(created_at) = ?",
            Integer.class,
            java.sql.Date.valueOf(targetDate)
        );

        BigDecimal estimatedSales = BigDecimal.valueOf(totalMeals == null ? 0 : totalMeals).multiply(BigDecimal.valueOf(28));
        BigDecimal safeCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        BigDecimal totalProfit = estimatedSales.subtract(safeCost);

        return new AnalysisOverviewResponse(
            targetDate.toString(),
            estimatedSales,
            safeCost,
            totalProfit,
            totalOrders == null ? 0 : totalOrders,
            totalMeals == null ? 0 : totalMeals,
            specialOrders == null ? 0 : specialOrders,
            aftersaleCount == null ? 0 : aftersaleCount
        );
    }

    @Override
    public List<CostEntryItem> costEntries(String month) {
        YearMonth targetMonth = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        return jdbcTemplate.query(
            "SELECT id, cost_date, cost_category, amount, remark, recorded_by FROM cost_entries WHERE cost_date BETWEEN ? AND ? ORDER BY cost_date DESC, id DESC",
            (rs, rowNum) -> new CostEntryItem(
                rs.getLong("id"),
                rs.getDate("cost_date").toLocalDate().toString(),
                rs.getString("cost_category"),
                rs.getBigDecimal("amount"),
                rs.getString("remark"),
                rs.getString("recorded_by")
            ),
            java.sql.Date.valueOf(targetMonth.atDay(1)),
            java.sql.Date.valueOf(targetMonth.atEndOfMonth())
        );
    }

    @Override
    @Transactional
    public Map<String, Object> createCostEntry(Map<String, Object> payload) {
        Object costDate = payload.get("costDate");
        Object costCategory = payload.get("costCategory");
        Object amount = payload.get("amount");
        if (costDate == null || costCategory == null || amount == null) {
            throw new BusinessException(ErrorCode.COST_ENTRY_INVALID, "成本录入数据不合法");
        }
        long id = insertAndReturnId(
            "INSERT INTO cost_entries (cost_date, cost_category, amount, remark, recorded_by) VALUES (?, ?, ?, ?, ?)",
            java.sql.Date.valueOf(String.valueOf(costDate)),
            String.valueOf(costCategory),
            new BigDecimal(String.valueOf(amount)),
            String.valueOf(payload.getOrDefault("remark", "")),
            String.valueOf(payload.getOrDefault("recordedBy", "后台客服"))
        );
        return Map.of("id", id, "status", "CREATED");
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
        if (keyHolder.getKey() == null) {
            return 0L;
        }
        return keyHolder.getKey().longValue();
    }
}
