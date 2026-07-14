package com.jzqs.app.dispatch.service.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcAreaMemoryModule implements AreaMemoryModule {
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAreaMemoryModule(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AreaMemoryItem> loadRoutingMemory(String areaCode, String scene) {
        String normalizedAreaCode = safe(areaCode).trim();
        if (normalizedAreaCode.isEmpty()) {
            return List.of();
        }
        String normalizedScene = safe(scene).trim();
        return jdbcTemplate.query(
            """
                SELECT id, area_code, memory_type, title, summary, applicable_scene, weight, status, source_correction_ids
                FROM dispatch_area_ai_memories
                WHERE area_code = ?
                  AND status = 'ACTIVE'
                  AND (? = '' OR applicable_scene IN ('ALL', ?))
                ORDER BY weight DESC, updated_at DESC
                """,
            (rs, rowNum) -> new AreaMemoryItem(
                rs.getLong("id"),
                rs.getString("area_code"),
                rs.getString("memory_type"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("applicable_scene"),
                rs.getInt("weight"),
                rs.getString("status"),
                readLongList(rs.getString("source_correction_ids"))
            ),
            normalizedAreaCode,
            normalizedScene,
            normalizedScene
        );
    }

    @Override
    public long recordCorrection(RecordCorrectionCommand command) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                """
                    INSERT INTO dispatch_area_ai_corrections
                    (area_code, correction_mode, merchant_instruction, merchant_reason_summary,
                     input_addresses_snapshot, original_order_ids, merchant_order_ids, final_ai_order_ids,
                     ai_interpretation_summary, replan_status, replan_error, confirmed_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, '', 'PENDING', '', ?)
                    """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, safe(command.areaCode()).trim());
            ps.setString(2, resolveCorrectionMode(command));
            ps.setString(3, safe(command.merchantInstruction()).trim());
            ps.setString(4, safe(command.merchantReasonSummary()).trim());
            ps.setString(5, writeJson(command.inputAddresses()));
            ps.setString(6, writeJson(command.originalOrderIds()));
            ps.setString(7, writeJson(command.merchantOrderIds()));
            ps.setString(8, writeJson(command.merchantOrderIds()));
            ps.setString(9, safe(command.confirmedBy()).trim().isEmpty() ? "system" : command.confirmedBy().trim());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("保存区域纠偏记录失败");
        }
        return keyHolder.getKey().longValue();
    }

    @Override
    public MergeMemoryResult mergeMemory(long correctionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT area_code, merchant_instruction, merchant_reason_summary
                FROM dispatch_area_ai_corrections
                WHERE id = ?
                """,
            correctionId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "纠偏记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        String areaCode = safe(row.get("area_code")).trim();
        String summary = buildMemorySummary(safe(row.get("merchant_reason_summary")), safe(row.get("merchant_instruction")));
        String title = buildMemoryTitle(summary);

        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
            """
                SELECT id, source_correction_ids, weight
                FROM dispatch_area_ai_memories
                WHERE area_code = ?
                  AND memory_type = 'ROUTE_PREFERENCE'
                  AND title = ?
                  AND status <> 'DELETED'
                ORDER BY updated_at DESC
                LIMIT 1
                """,
            areaCode,
            title
        );
        if (existingRows.isEmpty()) {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                    """
                        INSERT INTO dispatch_area_ai_memories
                        (area_code, memory_type, title, summary, applicable_scene, address_keys,
                         weight, status, last_verified_at, source_correction_ids, created_by, updated_by)
                        VALUES (?, 'ROUTE_PREFERENCE', ?, ?, 'ALL', '[]', 1, 'ACTIVE', CURRENT_TIMESTAMP, ?, 'system', 'system')
                        """,
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, areaCode);
                ps.setString(2, title);
                ps.setString(3, summary);
                ps.setString(4, writeJson(List.of(correctionId)));
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() == null) {
                throw new IllegalStateException("新增区域记忆失败");
            }
            return new MergeMemoryResult(keyHolder.getKey().longValue(), true, title, summary);
        }

        Map<String, Object> existing = existingRows.get(0);
        long memoryId = ((Number) existing.get("id")).longValue();
        int nextWeight = ((Number) existing.get("weight")).intValue() + 1;
        List<Long> mergedSourceIds = mergeSourceIds(readLongList(safe(existing.get("source_correction_ids"))), correctionId);
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_memories
                SET summary = ?,
                    weight = ?,
                    source_correction_ids = ?,
                    last_verified_at = CURRENT_TIMESTAMP,
                    updated_by = 'system'
                WHERE id = ?
                """,
            summary,
            nextWeight,
            writeJson(mergedSourceIds),
            memoryId
        );
        return new MergeMemoryResult(memoryId, false, title, summary);
    }

    private String resolveCorrectionMode(RecordCorrectionCommand command) {
        boolean hasInstruction = !safe(command.merchantInstruction()).trim().isEmpty();
        boolean hasSequenceChange = command.originalOrderIds() != null
            && command.merchantOrderIds() != null
            && !command.originalOrderIds().equals(command.merchantOrderIds());
        if (hasInstruction && hasSequenceChange) {
            return "MIXED";
        }
        if (hasSequenceChange) {
            return "DRAG";
        }
        return "CHAT";
    }

    private String buildMemorySummary(String reasonSummary, String instruction) {
        String normalizedReason = safe(reasonSummary).trim();
        if (!normalizedReason.isEmpty()) {
            return normalizedReason;
        }
        String normalizedInstruction = safe(instruction).trim();
        if (!normalizedInstruction.isEmpty()) {
            return normalizedInstruction;
        }
        return "已根据商家纠偏更新本区域排法经验";
    }

    private String buildMemoryTitle(String summary) {
        String normalized = summary.trim();
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24);
    }

    private List<Long> mergeSourceIds(List<Long> existing, long correctionId) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>(existing);
        merged.add(correctionId);
        return new ArrayList<>(merged);
    }

    private List<Long> readLongList(String raw) {
        try {
            return objectMapper.readValue(safe(raw), LONG_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("区域记忆序列化失败", ex);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
