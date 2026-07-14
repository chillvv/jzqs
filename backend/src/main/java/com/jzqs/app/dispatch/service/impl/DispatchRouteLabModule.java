package com.jzqs.app.dispatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchRouteLabStartResponse;
import com.jzqs.app.dispatch.api.DispatchRouteLabSimulateRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionItemResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionResponse;
import com.jzqs.app.dispatch.service.route.DispatchAiJobLogModule;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiPlanningResult;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import com.jzqs.app.dispatch.service.route.DispatchRouteFeatureExtractor;
import com.jzqs.app.dispatch.service.route.DispatchRoutePoint;
import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DispatchRouteLabModule {
    private static final String DEFAULT_OPERATOR = "system";
    private static final int JOB_LOG_MESSAGE_MAX_LENGTH = 255;
    private static final int JOB_LOG_REASON_MAX_LENGTH = 255;
    private static final int JOB_LOG_SOURCE_MAX_LENGTH = 32;
    private static final ExecutorService ROUTE_LAB_EXECUTOR =
        Executors.newFixedThreadPool(2, new RouteLabThreadFactory());

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DispatchAiJobLogModule dispatchAiJobLogModule;
    private final DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor;
    private final DispatchRouteAiRefineService dispatchRouteAiRefineService;

    DispatchRouteLabModule(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        DispatchAiJobLogModule dispatchAiJobLogModule,
        DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor,
        DispatchRouteAiRefineService dispatchRouteAiRefineService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dispatchAiJobLogModule = dispatchAiJobLogModule;
        this.dispatchRouteFeatureExtractor = dispatchRouteFeatureExtractor;
        this.dispatchRouteAiRefineService = dispatchRouteAiRefineService;
    }

    DispatchRouteSuggestionResponse simulateRouteLab(DispatchRouteLabSimulateRequest request, String operatorName) {
        RouteLabSimulationResult result = executeRouteLabSimulationCore(request, operatorName);
        DispatchRouteSuggestionResponse response = result.response();
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_ai_job_logs (
                    trigger_source,
                    serve_date,
                    meal_period,
                    area_code,
                    suggestion_id,
                    status,
                    suggestion_source,
                    reason_summary,
                    message,
                    metadata_json,
                    executed_by,
                    started_at,
                    finished_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            "TEST",
            java.sql.Date.valueOf(LocalDate.now()),
            "LAB",
            "路线实验室",
            response.suggestionId(),
            "SUCCESS",
            response.suggestionSource(),
            response.reasonSummary(),
            "实验室推演完成",
            buildThinkingMetadataJson(response, result.planningResult(), request.addresses(), "SUCCESS", "已完成", ""),
            operatorName
        );
        return response;
    }

    DispatchRouteLabStartResponse startRouteLabSimulation(DispatchRouteLabSimulateRequest request, String operatorName) {
        List<String> addresses = normalizeRouteLabAddresses(request.addresses());
        DispatchRouteLabSimulateRequest normalizedRequest = new DispatchRouteLabSimulateRequest(
            addresses,
            request.strategyMode(),
            request.anchorAddress()
        );
        DispatchAiSettingsSnapshot aiSettings = loadDispatchAiSettings();
        ensureAiReady(aiSettings);
        long logId = insertDispatchAiJobLog(
            "TEST",
            LocalDate.now(),
            "LAB",
            "路线实验室",
            null,
            "RUNNING",
            null,
            "",
            "已进入 AI 思考阶段",
            buildRouteLabProgressMetadataJson(normalizedRequest, "RUNNING", "AI 正在读取上下文", ""),
            operatorName
        );
        CompletableFuture.runAsync(
            () -> executeRouteLabSimulationAsync(logId, normalizedRequest, operatorName),
            ROUTE_LAB_EXECUTOR
        );
        return new DispatchRouteLabStartResponse(logId, "RUNNING", "实验室推演已启动，正在进入 AI 思考阶段");
    }

    DispatchAiJobLogResponse getDispatchAiJobLog(long logId) {
        List<DispatchAiJobLogResponse> logs = jdbcTemplate.query(
            """
                SELECT id,
                       trigger_source,
                       serve_date,
                       meal_period,
                       area_code,
                       suggestion_id,
                       status,
                       suggestion_source,
                       reason_summary,
                       message,
                       metadata_json,
                       executed_by,
                       started_at,
                       finished_at
                FROM dispatch_ai_job_logs
                WHERE id = ?
                """,
            (rs, rowNum) -> dispatchAiJobLogModule.readLog(rs),
            logId
        );
        if (logs.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未找到对应的运行日志");
        }
        return logs.get(0);
    }

    void deleteJobLogs(List<Long> ids) {
        dispatchAiJobLogModule.deleteLogsWithSuggestions(ids);
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

    private RouteLabSimulationResult executeRouteLabSimulationCore(
        DispatchRouteLabSimulateRequest request,
        String operatorName
    ) {
        List<String> addresses = normalizeRouteLabAddresses(request.addresses());
        DispatchAiSettingsSnapshot aiSettings = loadDispatchAiSettings();
        ensureAiReady(aiSettings);
        List<DispatchRouteFeatureExtractor.RouteAddressSeed> seeds = new ArrayList<>();
        long idCounter = 1L;
        for (String addr : addresses) {
            seeds.add(new DispatchRouteFeatureExtractor.RouteAddressSeed(idCounter++, addr.trim()));
        }
        List<DispatchRoutePoint> points = dispatchRouteFeatureExtractor.extractAll(seeds, "LAB", 0.0d, 0.0d);
        Map<Long, DispatchRoutePoint> pointMap = points.stream()
            .collect(Collectors.toMap(DispatchRoutePoint::orderId, point -> point));
        DispatchRouteAiPlanningResult planningResult = dispatchRouteAiRefineService.plan(
            new DispatchRouteAiRefineService.DispatchRouteAiPlanningRequest(
                "TEST",
                "LAB",
                aiSettings.apiBaseUrl(),
                aiSettings.apiKey(),
                aiSettings.aiModel(),
                aiSettings.promptTemplate(),
                request.anchorAddress(),
                request.anchorAddress(),
                request.strategyMode(),
                points,
                List.of()
            )
        );
        Map<Long, Integer> sequenceMap = buildSequenceMap(planningResult.finalOrderIds());
        RouteRunStatus routeRunStatus = resolveAiPrimaryRouteRunStatus(planningResult.summary());
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        LocalDate today = LocalDate.now();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                """
                    INSERT INTO dispatch_route_suggestions
                    (serve_date, meal_period, area_code, strategy_mode, anchor_name, anchor_address,
                     algorithm_version, ai_provider, ai_model, suggestion_source, reason_summary, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, 'v2', ?, ?, ?, ?, ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.setString(2, "LAB");
            ps.setString(3, "LAB");
            ps.setString(4, request.strategyMode());
            ps.setString(5, request.anchorAddress());
            ps.setString(6, request.anchorAddress());
            ps.setString(7, "deepseek");
            ps.setString(8, aiSettings.aiModel());
            ps.setString(9, "AI_ONLY");
            ps.setString(10, planningResult.summary());
            ps.setString(11, operatorName);
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
        DispatchRouteSuggestionResponse response = new DispatchRouteSuggestionResponse(
            suggestionId,
            request.strategyMode(),
            "AI_ONLY",
            planningResult.summary(),
            routeRunStatus.code(),
            routeRunStatus.label(),
            routeRunStatus.description(),
            items
        );
        return new RouteLabSimulationResult(response, planningResult);
    }

    private void executeRouteLabSimulationAsync(long logId, DispatchRouteLabSimulateRequest request, String operatorName) {
        try {
            updateDispatchAiJobLogProgress(
                logId,
                "RUNNING",
                "AI 已接收地址，正在生成排线结果",
                buildRouteLabProgressMetadataJson(request, "RUNNING", "AI 正在分析地址关系", "")
            );
            RouteLabSimulationResult result = executeRouteLabSimulationCore(request, operatorName);
            DispatchRouteSuggestionResponse response = result.response();
            finishDispatchAiJobLog(
                logId,
                "SUCCESS",
                response.runStatusDescription(),
                buildThinkingMetadataJson(response, result.planningResult(), request.addresses(), "SUCCESS", "已完成", ""),
                response.suggestionId(),
                response.suggestionSource(),
                response.reasonSummary()
            );
        } catch (Throwable ex) {
            finishDispatchAiJobLog(
                logId,
                "FAILED",
                "AI 推演失败：" + nullToEmpty(ex.getMessage()),
                buildRouteLabProgressMetadataJson(request, "FAILED", "执行失败", nullToEmpty(ex.getMessage())),
                null,
                null,
                ""
            );
        }
    }

    private void updateDispatchAiJobLogProgress(long logId, String status, String message, String metadataJson) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_job_logs
                SET status = ?,
                    message = ?,
                    metadata_json = ?
                WHERE id = ?
                """,
            status,
            fitDbText(message, JOB_LOG_MESSAGE_MAX_LENGTH),
            metadataJson,
            logId
        );
    }

    private long insertDispatchAiJobLog(
        String triggerSource,
        LocalDate serveDate,
        String mealPeriod,
        String areaCode,
        Long suggestionId,
        String status,
        String suggestionSource,
        String reasonSummary,
        String message,
        String metadataJson,
        String executedBy
    ) {
        return insertAndReturnId(
            """
                INSERT INTO dispatch_ai_job_logs (
                    trigger_source,
                    serve_date,
                    meal_period,
                    area_code,
                    suggestion_id,
                    status,
                    suggestion_source,
                    reason_summary,
                    message,
                    metadata_json,
                    executed_by,
                    started_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            triggerSource,
            serveDate == null ? null : java.sql.Date.valueOf(serveDate),
            mealPeriod,
            areaCode,
            suggestionId,
            status,
            fitDbText(suggestionSource, JOB_LOG_SOURCE_MAX_LENGTH),
            fitDbText(reasonSummary, JOB_LOG_REASON_MAX_LENGTH),
            fitDbText(message, JOB_LOG_MESSAGE_MAX_LENGTH),
            metadataJson,
            executedBy,
            Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private void finishDispatchAiJobLog(
        long logId,
        String status,
        String message,
        String metadataJson,
        Long suggestionId,
        String suggestionSource,
        String reasonSummary
    ) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_job_logs
                SET status = ?,
                    message = ?,
                    metadata_json = ?,
                    suggestion_id = ?,
                    suggestion_source = ?,
                    reason_summary = ?,
                    finished_at = ?
                WHERE id = ?
                """,
            status,
            fitDbText(message, JOB_LOG_MESSAGE_MAX_LENGTH),
            metadataJson,
            suggestionId,
            fitDbText(suggestionSource, JOB_LOG_SOURCE_MAX_LENGTH),
            fitDbText(reasonSummary, JOB_LOG_REASON_MAX_LENGTH),
            Timestamp.valueOf(LocalDateTime.now()),
            logId
        );
    }

    private String buildThinkingMetadataJson(
        DispatchRouteSuggestionResponse response,
        DispatchRouteAiPlanningResult planningResult,
        List<String> inputAddresses,
        String thinkingStatus,
        String currentPhase,
        String providerError
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("thinkingStatus", thinkingStatus);
        root.put("currentPhase", currentPhase);
        root.put("providerError", providerError == null ? "" : providerError);
        root.put("runStatusCode", response.runStatusCode());
        root.put("runStatusLabel", response.runStatusLabel());
        root.put("runStatusDescription", response.runStatusDescription());
        root.put("reasonSummary", response.reasonSummary());
        root.put("orderCount", response.items().size());
        root.put("aiAdjustedCount", response.items().size());
        root.put("clusterCount", 0);
        root.put("suggestionSource", response.suggestionSource());
        root.put("thinkingHeadline", response.runStatusDescription());
        root.put("summary", planningResult.summary());
        root.put("confidence", planningResult.confidence());
        root.set("inputAddresses", objectMapper.valueToTree(inputAddresses == null ? List.of() : inputAddresses));
        root.set("analysisSteps", objectMapper.valueToTree(planningResult.analysisSteps()));
        root.set("groups", objectMapper.createArrayNode());
        root.set("finalOrderIds", objectMapper.valueToTree(planningResult.finalOrderIds()));
        root.set("perOrderReasons", objectMapper.valueToTree(planningResult.perOrderReasons()));
        root.set("items", objectMapper.valueToTree(response.items()));
        return root.toString();
    }

    private String buildRouteLabProgressMetadataJson(
        DispatchRouteLabSimulateRequest request,
        String thinkingStatus,
        String currentPhase,
        String providerError
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        List<String> addresses = request.addresses() == null ? List.of() : request.addresses();
        root.put("thinkingStatus", thinkingStatus);
        root.put("currentPhase", currentPhase);
        root.put("providerError", providerError == null ? "" : providerError);
        root.put("runStatusCode", thinkingStatus);
        root.put("runStatusLabel", "RUNNING".equals(thinkingStatus) ? "AI 思考中" : "执行失败");
        root.put("runStatusDescription", "RUNNING".equals(thinkingStatus) ? "AI 正在读取上下文并生成排序结构" : providerError);
        root.put("orderCount", addresses.size());
        root.put("aiAdjustedCount", 0);
        root.put("clusterCount", 0);
        root.put("strategyMode", request.strategyMode());
        root.put("anchorAddress", request.anchorAddress());
        root.put("thinkingHeadline", "RUNNING".equals(thinkingStatus) ? "当前正在等待 DeepSeek 返回排序结论" : "AI 推演已失败");
        root.put("summary", "");
        root.put("confidence", 0.0d);
        root.set("analysisSteps", objectMapper.createArrayNode());
        root.set("groups", objectMapper.createArrayNode());
        root.set("finalOrderIds", objectMapper.createArrayNode());
        root.set("perOrderReasons", objectMapper.createArrayNode());
        root.set("inputAddresses", objectMapper.valueToTree(addresses));
        return root.toString();
    }

    private List<String> normalizeRouteLabAddresses(List<String> addresses) {
        List<String> normalized = (addresses == null ? List.<String>of() : addresses).stream()
            .filter(addr -> addr != null && !addr.isBlank())
            .map(String::trim)
            .map(addr -> addr.replaceFirst("^(?:\\d+\\s*[.、)）-]\\s*)+", "").trim())
            .toList();
        if (normalized.size() <= 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要 2 个有效地址才能进行推演测试");
        }
        return normalized;
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String fitDbText(String value, int maxLength) {
        String normalized = nullToEmpty(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private long insertAndReturnId(String sql, Object... args) {
        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
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

    private record RouteLabSimulationResult(
        DispatchRouteSuggestionResponse response,
        DispatchRouteAiPlanningResult planningResult
    ) {
    }

    private static final class RouteLabThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "dispatch-route-lab-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
