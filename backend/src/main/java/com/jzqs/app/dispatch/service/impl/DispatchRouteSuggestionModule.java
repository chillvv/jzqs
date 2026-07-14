package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionFeedbackResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionItemResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionResponse;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiPlanningResult;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import com.jzqs.app.dispatch.service.route.DispatchRouteFeatureExtractor;
import com.jzqs.app.dispatch.service.route.DispatchRoutePoint;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchRouteSuggestionModule {
    private static final String DEFAULT_OPERATOR = "system";

    private final JdbcTemplate jdbcTemplate;
    private final DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor;
    private final DispatchRouteAiRefineService dispatchRouteAiRefineService;

    DispatchRouteSuggestionModule(
        JdbcTemplate jdbcTemplate,
        DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor,
        DispatchRouteAiRefineService dispatchRouteAiRefineService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatchRouteFeatureExtractor = dispatchRouteFeatureExtractor;
        this.dispatchRouteAiRefineService = dispatchRouteAiRefineService;
    }

    DispatchRouteSuggestionResponse suggestAreaRoute(String areaCode, DispatchRouteSuggestionRequest request) {
        LocalDate serveDate = LocalDate.parse(request.serveDate());
        String mealPeriod = normalizedMealPeriod(request.mealPeriod());
        String normalizedAreaCode = requireAreaCode(areaCode);

        List<RouteOrderSeed> existingOrders = loadAreaRouteSeeds(normalizedAreaCode, mealPeriod, serveDate);
        if (existingOrders.size() <= 1) {
            return new DispatchRouteSuggestionResponse(
                0L,
                request.strategyMode(),
                "NO_ORDERS",
                "该区域暂无可优化排线路径",
                "NO_ORDERS",
                "暂无可测试订单",
                "当前区域订单不足 2 单，无法形成可比较的排线路径。",
                List.of()
            );
        }

        List<DispatchRoutePoint> points = dispatchRouteFeatureExtractor.extractAll(
            existingOrders.stream()
                .map(order -> new DispatchRouteFeatureExtractor.RouteAddressSeed(order.orderId(), order.deliveryAddress()))
                .toList(),
            normalizedAreaCode,
            0.0d,
            0.0d
        );
        Map<Long, DispatchRoutePoint> pointMap = points.stream()
            .collect(Collectors.toMap(DispatchRoutePoint::orderId, point -> point));
        DispatchAiSettingsSnapshot aiSettings = loadDispatchAiSettings();
        ensureAiReady(aiSettings);
        DispatchRouteAiPlanningResult planningResult = dispatchRouteAiRefineService.plan(
            new DispatchRouteAiRefineService.DispatchRouteAiPlanningRequest(
                "PRODUCTION",
                normalizedAreaCode,
                aiSettings.apiBaseUrl(),
                aiSettings.apiKey(),
                aiSettings.aiModel(),
                aiSettings.promptTemplate(),
                request.anchorName(),
                request.anchorAddress(),
                request.strategyMode(),
                points,
                loadHistoryPreferenceSummary(normalizedAreaCode)
            )
        );
        Map<Long, Integer> sequenceMap = buildSequenceMap(planningResult.finalOrderIds());
        RouteRunStatus routeRunStatus = resolveAiPrimaryRouteRunStatus(planningResult.summary());

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        String insertSuggestionSql = """
            INSERT INTO dispatch_route_suggestions
            (serve_date, meal_period, area_code, strategy_mode, anchor_name, anchor_address,
             algorithm_version, ai_provider, ai_model, suggestion_source, reason_summary, created_by)
            VALUES (?, ?, ?, ?, ?, ?, 'v2', ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(insertSuggestionSql, Statement.RETURN_GENERATED_KEYS);
            ps.setDate(1, java.sql.Date.valueOf(serveDate));
            ps.setString(2, mealPeriod);
            ps.setString(3, normalizedAreaCode);
            ps.setString(4, request.strategyMode());
            ps.setString(5, request.anchorName());
            ps.setString(6, request.anchorAddress());
            ps.setString(7, "deepseek");
            ps.setString(8, aiSettings.aiModel());
            ps.setString(9, "AI_ONLY");
            ps.setString(10, planningResult.summary());
            ps.setString(11, DEFAULT_OPERATOR);
            return ps;
        }, keyHolder);
        long suggestionId = keyHolder.getKey().longValue();

        String insertItemSql = """
            INSERT INTO dispatch_route_suggestion_items
            (suggestion_id, order_id, suggested_sequence, base_score, adjusted_score, is_ai_adjusted)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        List<Object[]> batchArgs = new ArrayList<>();
        for (Long orderId : planningResult.finalOrderIds()) {
            int adjustedSequence = sequenceMap.get(orderId);
            batchArgs.add(new Object[] {
                suggestionId,
                orderId,
                adjustedSequence,
                0.0d,
                0.0d,
                true
            });
        }
        jdbcTemplate.batchUpdate(insertItemSql, batchArgs);

        List<DispatchRouteSuggestionItemResponse> items = planningResult.finalOrderIds().stream()
            .map(orderId -> {
                DispatchRoutePoint point = pointMap.get(orderId);
                int adjustedSequence = sequenceMap.get(orderId);
                return new DispatchRouteSuggestionItemResponse(
                    orderId,
                    adjustedSequence,
                    true,
                    adjustedSequence,
                    point == null ? "" : point.addressLabel(),
                    "",
                    "",
                    "",
                    "",
                    point == null ? 0 : point.neighborCount(),
                    resolveAiPrimaryReason(orderId, planningResult)
                );
            })
            .toList();

        return new DispatchRouteSuggestionResponse(
            suggestionId,
            request.strategyMode(),
            "AI_ONLY",
            planningResult.summary(),
            routeRunStatus.code(),
            routeRunStatus.label(),
            routeRunStatus.description(),
            items
        );
    }

    DispatchRouteSuggestionFeedbackResponse saveRouteSuggestionFeedback(
        String areaCode,
        DispatchRouteSuggestionFeedbackRequest request,
        String operatorName
    ) {
        List<Map<String, Object>> suggestionInfo = jdbcTemplate.queryForList(
            """
                SELECT serve_date, meal_period
                FROM dispatch_route_suggestions
                WHERE id = ?
                """,
            request.suggestionId()
        );
        if (suggestionInfo.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "建议不存在");
        }
        LocalDate serveDate = ((java.sql.Date) suggestionInfo.get(0).get("serve_date")).toLocalDate();
        String mealPeriod = (String) suggestionInfo.get(0).get("meal_period");

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                """
                    INSERT INTO dispatch_route_feedback
                    (suggestion_id, area_code, serve_date, meal_period, confirmed_by,
                     change_count, accepted_directly, feedback_summary)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, request.suggestionId());
            ps.setString(2, areaCode);
            ps.setDate(3, java.sql.Date.valueOf(serveDate));
            ps.setString(4, mealPeriod);
            ps.setString(5, operatorName);
            ps.setInt(6, request.changeCount());
            ps.setBoolean(7, request.acceptedDirectly());
            ps.setString(8, request.feedbackSummary() != null ? request.feedbackSummary() : "");
            return ps;
        }, keyHolder);

        return new DispatchRouteSuggestionFeedbackResponse(keyHolder.getKey().longValue());
    }

    void preRouteTomorrowAreas() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<String> mealPeriods = List.of("LUNCH", "DINNER");
        List<String> areaCodes = jdbcTemplate.queryForList(
            """
                SELECT DISTINCT area_code
                FROM dispatch_area_bindings
                WHERE default_rider_profile_id IS NOT NULL
                """,
            String.class
        );

        for (String areaCode : areaCodes) {
            for (String mealPeriod : mealPeriods) {
                try {
                    DispatchRouteSuggestionRequest request = new DispatchRouteSuggestionRequest(
                        tomorrow.toString(),
                        mealPeriod,
                        "NEAR_TO_FAR",
                        "五环天地",
                        "五环天地",
                        true
                    );
                    suggestAreaRoute(areaCode, request);
                } catch (Exception e) {
                    System.err.println("预排失败: area=" + areaCode + ", meal=" + mealPeriod + ", err=" + e.getMessage());
                }
            }
        }
    }

    private List<RouteOrderSeed> loadAreaRouteSeeds(String areaCode, String mealPeriod, LocalDate serveDate) {
        return jdbcTemplate.query(
            """
                SELECT
                    mso.id AS order_id,
                    ca.address_line AS delivery_address
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                JOIN customer_addresses ca ON ca.id = mso.address_id
                WHERE da.area_code = ?
                  AND doo.serve_date = ?
                  AND (? IS NULL OR COALESCE(mso.delivery_meal_period, mso.meal_period) = ?)
                ORDER BY
                    CASE WHEN da.status = 'DELIVERED' THEN 1 ELSE 0 END,
                    da.sequence_number,
                    da.id
                """,
            (rs, rowNum) -> new RouteOrderSeed(
                rs.getLong("order_id"),
                rs.getString("delivery_address")
            ),
            areaCode,
            serveDate,
            mealPeriod,
            mealPeriod
        );
    }

    private Map<Long, Integer> buildSequenceMap(List<Long> orderIds) {
        Map<Long, Integer> sequenceMap = new LinkedHashMap<>();
        for (int i = 0; i < orderIds.size(); i++) {
            sequenceMap.put(orderIds.get(i), i + 1);
        }
        return sequenceMap;
    }

    private RouteRunStatus resolveAiPrimaryRouteRunStatus(String summary) {
        return new RouteRunStatus(
            "AI_SUCCESS",
            "AI 已完成排线",
            summary == null || summary.isBlank() ? "AI 已依据当前上下文生成最终排线顺序。" : summary
        );
    }

    private String resolveAiPrimaryReason(Long orderId, DispatchRouteAiPlanningResult planningResult) {
        return planningResult.perOrderReasons().stream()
            .filter(reason -> reason.orderId() == orderId)
            .map(DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason::reason)
            .filter(reason -> reason != null && !reason.isBlank())
            .findFirst()
            .orElse(planningResult.summary());
    }

    private List<String> loadHistoryPreferenceSummary(String areaCode) {
        List<String> summaries = jdbcTemplate.query(
            """
                SELECT feedback_summary
                FROM dispatch_route_feedback
                WHERE area_code = ?
                  AND feedback_summary IS NOT NULL
                  AND feedback_summary <> ''
                ORDER BY confirmed_at DESC
                LIMIT 3
                """,
            (rs, rowNum) -> rs.getString("feedback_summary"),
            areaCode
        );
        return summaries == null ? List.of() : summaries;
    }

    private DispatchAiSettingsSnapshot loadDispatchAiSettings() {
        List<DispatchAiSettingsSnapshot> snapshots = jdbcTemplate.query(
            """
                SELECT ai_enabled, api_base_url, api_key, ai_model, ai_prompt_template, balance_available
                FROM dispatch_ai_settings
                WHERE id = 1
                """,
            (rs, rowNum) -> new DispatchAiSettingsSnapshot(
                rs.getBoolean("ai_enabled"),
                rs.getString("api_base_url"),
                rs.getString("api_key"),
                rs.getString("ai_model"),
                rs.getString("ai_prompt_template"),
                rs.getBoolean("balance_available")
            )
        );
        if (snapshots.isEmpty()) {
            return new DispatchAiSettingsSnapshot(true, "https://api.deepseek.com", "", "deepseek-chat", "", false);
        }
        return snapshots.get(0);
    }

    private void ensureAiReady(DispatchAiSettingsSnapshot aiSettings) {
        if (!aiSettings.aiEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 未启用，请先完成 AI 配置");
        }
        if (nullToEmpty(aiSettings.apiKey()).isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI Key 未配置，请先补全 API Key");
        }
        if (!aiSettings.balanceAvailable()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 额度不足或尚未刷新，请先刷新额度");
        }
    }

    private String requireAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择区域");
        }
        return areaCode.trim();
    }

    private String normalizedMealPeriod(String mealPeriod) {
        return mealPeriod == null || mealPeriod.isBlank() ? null : mealPeriod.trim().toUpperCase();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record DispatchAiSettingsSnapshot(
        boolean aiEnabled,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String promptTemplate,
        boolean balanceAvailable
    ) {
    }

    private record RouteRunStatus(
        String code,
        String label,
        String description
    ) {
    }

    private record RouteOrderSeed(long orderId, String deliveryAddress) {
    }
}
