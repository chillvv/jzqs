package com.jzqs.app.dispatch.service.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcDispatchAiJobLogModule implements DispatchAiJobLogModule {
    private static final long STALE_ROUTE_LAB_TIMEOUT_MINUTES = 3L;
    private static final int JOB_LOG_MESSAGE_MAX_LENGTH = 255;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STALE_ROUTE_LAB_MESSAGE = "历史任务未正确收尾，已自动标记失败，请重新测试。";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDispatchAiJobLogModule(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DispatchAiJobLogResponse readLog(ResultSet resultSet) throws SQLException {
        Timestamp startedAt = resultSet.getTimestamp("started_at");
        Timestamp finishedAt = resultSet.getTimestamp("finished_at");
        DispatchAiJobLogResponse response = new DispatchAiJobLogResponse(
            resultSet.getLong("id"),
            resolveRunType(resultSet.getString("trigger_source")),
            nullToEmpty(resultSet.getString("trigger_source")),
            resultSet.getDate("serve_date") == null ? "" : resultSet.getDate("serve_date").toLocalDate().toString(),
            nullToEmpty(resultSet.getString("meal_period")),
            nullToEmpty(resultSet.getString("area_code")),
            resultSet.getObject("suggestion_id") == null ? null : resultSet.getLong("suggestion_id"),
            nullToEmpty(resultSet.getString("status")),
            nullToEmpty(resultSet.getString("suggestion_source")),
            nullToEmpty(resultSet.getString("reason_summary")),
            nullToEmpty(resultSet.getString("message")),
            nullToEmpty(resultSet.getString("metadata_json")),
            nullToEmpty(resultSet.getString("executed_by")),
            formatTimestamp(startedAt),
            formatTimestamp(finishedAt)
        );
        if (!isStaleRouteLabLog(response, startedAt, finishedAt)) {
            return response;
        }
        Timestamp resolvedFinishedAt = Timestamp.valueOf(LocalDateTime.now());
        String staleMetadata = buildStaleRouteLabMetadata(response.metadataJson(), STALE_ROUTE_LAB_MESSAGE);
        markStaleRouteLabLogFailed(response.id(), STALE_ROUTE_LAB_MESSAGE, staleMetadata, resolvedFinishedAt);
        return new DispatchAiJobLogResponse(
            response.id(),
            response.runType(),
            response.triggerSource(),
            response.serveDate(),
            response.mealPeriod(),
            response.areaCode(),
            response.suggestionId(),
            "FAILED",
            response.suggestionSource(),
            response.reasonSummary(),
            STALE_ROUTE_LAB_MESSAGE,
            staleMetadata,
            response.executedBy(),
            response.startedAt(),
            formatTimestamp(resolvedFinishedAt)
        );
    }

    @Override
    public void deleteLogsWithSuggestions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] parameters = ids.toArray();
        jdbcTemplate.update(
            "DELETE FROM dispatch_route_suggestion_items WHERE suggestion_id IN (SELECT suggestion_id FROM dispatch_ai_job_logs WHERE id IN (" + placeholders + ") AND suggestion_id IS NOT NULL)",
            parameters
        );
        jdbcTemplate.update(
            "DELETE FROM dispatch_route_suggestions WHERE id IN (SELECT suggestion_id FROM dispatch_ai_job_logs WHERE id IN (" + placeholders + ") AND suggestion_id IS NOT NULL)",
            parameters
        );
        jdbcTemplate.update(
            "DELETE FROM dispatch_ai_job_logs WHERE id IN (" + placeholders + ")",
            parameters
        );
    }

    private boolean isStaleRouteLabLog(
        DispatchAiJobLogResponse response,
        Timestamp startedAt,
        Timestamp finishedAt
    ) {
        if (!"RUNNING".equalsIgnoreCase(nullToEmpty(response.status()))) {
            return false;
        }
        if (!"TEST".equalsIgnoreCase(nullToEmpty(response.runType()))) {
            return false;
        }
        if (finishedAt != null || startedAt == null) {
            return false;
        }
        return startedAt.toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(STALE_ROUTE_LAB_TIMEOUT_MINUTES));
    }

    private String buildStaleRouteLabMetadata(String rawMetadataJson, String message) {
        ObjectNode root = objectMapper.createObjectNode();
        try {
            String normalized = nullToEmpty(rawMetadataJson).trim();
            if (!normalized.isEmpty()) {
                JsonNode parsed = objectMapper.readTree(normalized);
                if (parsed != null && parsed.isObject()) {
                    root = (ObjectNode) parsed.deepCopy();
                }
            }
        } catch (Exception ignored) {
        }
        root.put("thinkingStatus", "FAILED");
        root.put("currentPhase", "\u6267\u884c\u4e2d\u65ad");
        root.put("providerError", message);
        root.put("runStatusCode", "FAILED_INTERNAL");
        root.put("runStatusLabel", "\u6267\u884c\u4e2d\u65ad");
        root.put("runStatusDescription", message);
        root.put("thinkingHeadline", "\u5386\u53f2\u4efb\u52a1\u672a\u6b63\u786e\u6536\u5c3e");
        return root.toString();
    }

    private void markStaleRouteLabLogFailed(long logId, String staleMessage, String metadataJson, Timestamp finishedAt) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_job_logs
                SET status = ?,
                    message = ?,
                    metadata_json = ?,
                    finished_at = ?
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND finished_at IS NULL
                """,
            "FAILED",
            fitDbText(staleMessage, JOB_LOG_MESSAGE_MAX_LENGTH),
            metadataJson,
            finishedAt,
            logId
        );
    }

    private String resolveRunType(String triggerSource) {
        String normalized = nullToEmpty(triggerSource).trim().toUpperCase();
        if ("TEST".equals(normalized) || "TEST_LAB".equals(normalized) || "MANUAL_TEST".equals(normalized)) {
            return "TEST";
        }
        return "PRODUCTION";
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
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
}
