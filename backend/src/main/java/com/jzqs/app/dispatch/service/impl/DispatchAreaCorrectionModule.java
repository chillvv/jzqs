package com.jzqs.app.dispatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionConfirmRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewRequest;
import com.jzqs.app.dispatch.api.DispatchAreaAiCorrectionPreviewResponse;
import com.jzqs.app.dispatch.service.route.AreaMemoryModule;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineResult;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import com.jzqs.app.dispatch.service.route.DispatchRouteFeatureExtractor;
import com.jzqs.app.dispatch.service.route.DispatchRoutePoint;
import com.jzqs.app.dispatch.service.route.RoutingContextAssemblerModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DispatchAreaCorrectionModule {
    private static final String DEFAULT_OPERATOR = "system";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor;
    private final DispatchRouteAiRefineService dispatchRouteAiRefineService;
    private final AreaMemoryModule areaMemoryModule;
    private final RoutingContextAssemblerModule routingContextAssemblerModule;

    DispatchAreaCorrectionModule(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        DispatchRouteFeatureExtractor dispatchRouteFeatureExtractor,
        DispatchRouteAiRefineService dispatchRouteAiRefineService,
        AreaMemoryModule areaMemoryModule,
        RoutingContextAssemblerModule routingContextAssemblerModule
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dispatchRouteFeatureExtractor = dispatchRouteFeatureExtractor;
        this.dispatchRouteAiRefineService = dispatchRouteAiRefineService;
        this.areaMemoryModule = areaMemoryModule;
        this.routingContextAssemblerModule = routingContextAssemblerModule;
    }

    DispatchAreaAiCorrectionPreviewResponse previewAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionPreviewRequest request,
        String operatorName
    ) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        List<Long> originalOrderIds = normalizeCorrectionOrderIds(request.originalOrderIds(), "原始顺序不能为空");
        List<Long> merchantOrderIds = normalizeCorrectionOrderIds(request.merchantOrderIds(), "商家顺序不能为空");
        validateSameOrderSet(originalOrderIds, merchantOrderIds, "商家顺序必须与原始顺序保持同一批订单");
        List<String> inputAddresses = normalizeCorrectionAddresses(request.inputAddresses(), originalOrderIds.size());
        String confirmedBy = normalizeOperatorName(operatorName);

        long correctionId = areaMemoryModule.recordCorrection(new AreaMemoryModule.RecordCorrectionCommand(
            normalizedAreaCode,
            inputAddresses,
            originalOrderIds,
            merchantOrderIds,
            request.merchantInstruction(),
            request.merchantReasonSummary(),
            confirmedBy
        ));
        updateCorrectionRouteRunId(correctionId, request.routeRunId());

        PreviewComputationResult previewResult = computeCorrectionPreview(
            correctionId,
            normalizedAreaCode,
            inputAddresses,
            merchantOrderIds,
            request.merchantInstruction(),
            request.merchantReasonSummary()
        );
        persistCorrectionPreviewResult(correctionId, previewResult);
        return previewResult.response();
    }

    DispatchAreaAiCorrectionPreviewResponse confirmAreaAiCorrection(
        String areaCode,
        DispatchAreaAiCorrectionConfirmRequest request,
        String operatorName
    ) {
        String normalizedAreaCode = requireAreaCode(areaCode);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT id, area_code, original_order_ids, merchant_instruction, merchant_reason_summary,
                       ai_interpretation_summary, replan_status, replan_error
                FROM dispatch_area_ai_corrections
                WHERE id = ?
                """,
            request.correctionId()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "纠偏记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        String persistedAreaCode = safeString(row.get("area_code")).trim();
        if (!persistedAreaCode.equals(normalizedAreaCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "纠偏记录与当前区域不匹配");
        }

        List<Long> finalOrderIds = normalizeCorrectionOrderIds(request.finalOrderIds(), "最终顺序不能为空");
        validateSameOrderSet(
            readLongListJson(safeString(row.get("original_order_ids"))),
            finalOrderIds,
            "最终顺序必须与原始顺序保持同一批订单"
        );
        String confirmedBy = normalizeOperatorName(operatorName);
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_corrections
                SET final_ai_order_ids = ?,
                    confirmed_by = ?,
                    confirmed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            writeJson(finalOrderIds),
            confirmedBy,
            request.correctionId()
        );
        AreaMemoryModule.MergeMemoryResult merged = areaMemoryModule.mergeMemory(request.correctionId());
        return new DispatchAreaAiCorrectionPreviewResponse(
            request.correctionId(),
            safeString(row.get("ai_interpretation_summary")).trim(),
            safeString(row.get("replan_status")).trim(),
            safeString(row.get("replan_error")).trim(),
            finalOrderIds,
            List.of(new DispatchAreaAiCorrectionPreviewResponse.MemoryCandidateItem(
                "ROUTE_PREFERENCE",
                merged.title(),
                merged.summary()
            ))
        );
    }

    private PreviewComputationResult computeCorrectionPreview(
        long correctionId,
        String areaCode,
        List<String> inputAddresses,
        List<Long> merchantOrderIds,
        String merchantInstruction,
        String merchantReasonSummary
    ) {
        List<DispatchRoutePoint> points = buildCorrectionRoutePoints(areaCode, merchantOrderIds, inputAddresses);
        String immediateCorrection = buildImmediateCorrection(merchantInstruction, merchantReasonSummary);
        String routingContext = routingContextAssemblerModule.buildRoutingContext(
            areaCode,
            new RoutingContextAssemblerModule.CurrentTask("AREA_CORRECTION", inputAddresses, merchantOrderIds),
            immediateCorrection
        );
        List<DispatchAreaAiCorrectionPreviewResponse.MemoryCandidateItem> candidates = buildMemoryCandidates(
            merchantInstruction,
            merchantReasonSummary
        );
        DispatchAiSettingsSnapshot aiSettings = loadDispatchAiSettings();
        if (!aiSettings.aiEnabled() || safeString(aiSettings.apiKey()).trim().isEmpty()) {
            return new PreviewComputationResult(
                correctionId,
                new DispatchAreaAiCorrectionPreviewResponse(
                    correctionId,
                    immediateCorrection.isBlank() ? "已记录商家纠偏，当前未启用 AI 重排。" : immediateCorrection,
                    "SKIPPED",
                    "AI 未启用或 Key 缺失，已保留商家当前顺序。",
                    merchantOrderIds,
                    candidates
                )
            );
        }

        try {
            DispatchRouteAiRefineResult refineResult = dispatchRouteAiRefineService.refine(
                new DispatchRouteAiRefineService.DispatchRouteAiRequest(
                    true,
                    aiSettings.apiBaseUrl(),
                    aiSettings.apiKey(),
                    aiSettings.aiModel(),
                    aiSettings.promptTemplate(),
                    "区域纠偏工作台",
                    areaCode,
                    "MERCHANT_CORRECTION",
                    points,
                    merchantOrderIds,
                    List.of(routingContext),
                    Math.max(1, merchantOrderIds.size()),
                    merchantOrderIds.size(),
                    immediateCorrection.isBlank() ? "AI 已参考商家纠偏重新生成当前顺序" : immediateCorrection
                )
            );
            return new PreviewComputationResult(
                correctionId,
                new DispatchAreaAiCorrectionPreviewResponse(
                    correctionId,
                    safeString(refineResult.reasonSummary()).trim().isEmpty()
                        ? "AI 已参考商家纠偏重新生成当前顺序"
                        : refineResult.reasonSummary(),
                    "SUCCESS",
                    safeString(refineResult.failureReason()).trim(),
                    refineResult.orderIds(),
                    candidates
                )
            );
        } catch (BusinessException ex) {
            return new PreviewComputationResult(
                correctionId,
                new DispatchAreaAiCorrectionPreviewResponse(
                    correctionId,
                    immediateCorrection.isBlank() ? "AI 重排失败，已回退为商家当前顺序。" : immediateCorrection,
                    "FAILED",
                    ex.getMessage(),
                    merchantOrderIds,
                    candidates
                )
            );
        }
    }

    private void updateCorrectionRouteRunId(long correctionId, Long routeRunId) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_corrections
                SET route_run_id = ?
                WHERE id = ?
                """,
            routeRunId,
            correctionId
        );
    }

    private void persistCorrectionPreviewResult(long correctionId, PreviewComputationResult previewResult) {
        DispatchAreaAiCorrectionPreviewResponse response = previewResult.response();
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_corrections
                SET final_ai_order_ids = ?,
                    ai_interpretation_summary = ?,
                    replan_status = ?,
                    replan_error = ?
                WHERE id = ?
                """,
            writeJson(response.finalOrderIds()),
            safeString(response.aiInterpretationSummary()).trim(),
            safeString(response.replanStatus()).trim(),
            safeString(response.replanError()).trim(),
            correctionId
        );
    }

    private List<DispatchRoutePoint> buildCorrectionRoutePoints(
        String areaCode,
        List<Long> orderIds,
        List<String> inputAddresses
    ) {
        return dispatchRouteFeatureExtractor.extractAll(
            zipCorrectionAddressSeeds(orderIds, inputAddresses),
            areaCode,
            0.0d,
            0.0d
        );
    }

    private List<DispatchRouteFeatureExtractor.RouteAddressSeed> zipCorrectionAddressSeeds(
        List<Long> orderIds,
        List<String> inputAddresses
    ) {
        List<DispatchRouteFeatureExtractor.RouteAddressSeed> seeds = new ArrayList<>();
        for (int index = 0; index < orderIds.size(); index++) {
            seeds.add(new DispatchRouteFeatureExtractor.RouteAddressSeed(orderIds.get(index), inputAddresses.get(index)));
        }
        return seeds;
    }

    private List<DispatchAreaAiCorrectionPreviewResponse.MemoryCandidateItem> buildMemoryCandidates(
        String merchantInstruction,
        String merchantReasonSummary
    ) {
        String summary = buildImmediateCorrection(merchantInstruction, merchantReasonSummary);
        if (summary.isBlank()) {
            return List.of();
        }
        String title = summary.length() <= 24 ? summary : summary.substring(0, 24);
        return List.of(new DispatchAreaAiCorrectionPreviewResponse.MemoryCandidateItem(
            "ROUTE_PREFERENCE",
            title,
            summary
        ));
    }

    private String buildImmediateCorrection(String merchantInstruction, String merchantReasonSummary) {
        String normalizedReason = safeString(merchantReasonSummary).trim();
        if (!normalizedReason.isEmpty()) {
            return normalizedReason;
        }
        return safeString(merchantInstruction).trim();
    }

    private List<Long> normalizeCorrectionOrderIds(List<Long> orderIds, String message) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return orderIds.stream().map(id -> {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单编号不能为空");
            }
            return id;
        }).toList();
    }

    private List<String> normalizeCorrectionAddresses(List<String> inputAddresses, int expectedSize) {
        if (inputAddresses == null || inputAddresses.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址快照不能为空");
        }
        List<String> normalized = inputAddresses.stream()
            .map(address -> safeString(address).trim())
            .toList();
        if (normalized.size() != expectedSize || normalized.stream().anyMatch(String::isEmpty)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "地址快照必须与订单顺序一一对应");
        }
        return normalized;
    }

    private void validateSameOrderSet(List<Long> expectedOrderIds, List<Long> actualOrderIds, String message) {
        if (expectedOrderIds.size() != actualOrderIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        if (!new java.util.HashSet<>(expectedOrderIds).equals(new java.util.HashSet<>(actualOrderIds))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private String normalizeOperatorName(String operatorName) {
        String normalized = safeString(operatorName).trim();
        return normalized.isEmpty() ? DEFAULT_OPERATOR : normalized;
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

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("区域纠偏序列化失败", ex);
        }
    }

    private List<Long> readLongListJson(String rawJson) {
        try {
            return objectMapper.readValue(
                safeString(rawJson),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class)
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String requireAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择区域");
        }
        return areaCode.trim();
    }

    private record PreviewComputationResult(
        long correctionId,
        DispatchAreaAiCorrectionPreviewResponse response
    ) {
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
}
