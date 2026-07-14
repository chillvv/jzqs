package com.jzqs.app.dispatch.service.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DispatchRouteAiRefineService {
    private static final double MIN_CONFIDENCE = 0.55d;
        private static final int MAX_PROVIDER_ERROR_LENGTH = 280;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DispatchRouteAiRefineService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    DispatchRouteAiRefineService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public DispatchRouteAiPlanningResult plan(DispatchRouteAiPlanningRequest request) {
        if (request.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 未启用或 Key 缺失");
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(request.apiBaseUrl()) + "/chat/completions"))
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(buildPlanningPromptBody(request), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "DeepSeek 请求失败，HTTP " + response.statusCode() + "，" + summarizeProviderError(response.body())
                );
            }
            return validatePlanningResult(extractContent(response.body()), request.points());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "DeepSeek 调用异常: " + ex.getMessage());
        }
    }

    public DispatchRouteAiRefineResult refine(DispatchRouteAiRequest request) {
        if (!request.aiEnabled() || request.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 未启用或 Key 缺失");
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(request.apiBaseUrl()) + "/chat/completions"))
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(buildPromptBody(request), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "DeepSeek 请求失败，HTTP " + response.statusCode() + "，" + summarizeProviderError(response.body())
                );
            }
            return validateOrFallback(
                extractContent(response.body()),
                request.ruleCandidateOrderIds(),
                request.maxPositionShift(),
                request.maxChangedOrders(),
                request.defaultReasonSummary()
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "DeepSeek 调用异常: " + ex.getMessage());
        }
    }

    public DispatchRouteAiRefineResult validateOrFallback(
        String rawJson,
        List<Long> fallbackOrderIds,
        int maxPositionShift,
        int maxChangedOrders,
        String defaultReasonSummary
    ) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            List<Long> candidate = readOrderIds(root.path("orderIds"));
            if (!isSameSet(candidate, fallbackOrderIds)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型返回订单集合不合法");
            }
            if (exceedsBounds(candidate, fallbackOrderIds, maxPositionShift, maxChangedOrders)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型调整幅度超限");
            }
            double confidence = root.path("confidence").asDouble(1.0d);
            if (confidence < MIN_CONFIDENCE) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型置信度过低");
            }
            String reasonSummary = root.path("reasonSummary").asText(defaultReasonSummary);
            boolean aiAdjusted = !candidate.equals(fallbackOrderIds);
            return new DispatchRouteAiRefineResult(
                candidate,
                reasonSummary.isBlank() ? defaultReasonSummary : reasonSummary,
                aiAdjusted ? "RULE_PLUS_AI" : "RULE_ONLY",
                aiAdjusted,
                ""
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出解析失败");
        }
    }

    String buildPromptBody(DispatchRouteAiRequest request) throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", request.aiModel());
        payload.put("temperature", 0.2d);
        payload.putObject("response_format").put("type", "json_object");
        ArrayNode messages = payload.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put("content", buildSystemPrompt(request.promptTemplate()));
        messages.addObject()
            .put("role", "user")
            .put("content", buildUserContext(request));
        return objectMapper.writeValueAsString(payload);
    }

    private String buildSystemPrompt(String promptTemplate) {
        String basePrompt = """
            你是区域配送路线微调助手，不是地图导航系统。
            你会收到一个候选顺序，你只能在候选顺序附近做轻量修正。
            你的目标是在不改变订单集合和整体方向的前提下优化配送路线。
            你必须：
            1. 返回完整且唯一的 orderIds；
            2. 不允许缺单、重复单；
            3. 不允许输出候选顺序之外的订单；
            4. 不允许大幅度重排；
            5. 只输出 JSON。
            返回格式：
            {"reasonSummary":"...","confidence":0.88,"orderIds":[1,2,3]}
            """;
        String template = promptTemplate == null ? "" : promptTemplate.trim();
        if (template.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n以下是业务补充偏好，请在不违反上述铁律的前提下执行：\n" + template;
    }

    String buildPlanningPromptBody(DispatchRouteAiPlanningRequest request) throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", request.aiModel());
        payload.put("temperature", 0.2d);
        payload.putObject("response_format").put("type", "json_object");
        ArrayNode messages = payload.putArray("messages");
        messages.addObject()
            .put("role", "system")
            .put("content", buildPlanningSystemPrompt(request.promptTemplate()));
        messages.addObject()
            .put("role", "user")
            .put("content", buildPlanningUserContext(request));
        return objectMapper.writeValueAsString(payload);
    }

    private String buildPlanningSystemPrompt(String promptTemplate) {
        String basePrompt = """
            你是配送排线助手，不是闲聊助手，也不是地图导航系统。
            你的任务是依据区域、锚点、地址文本语义和历史偏好，为本次配送任务生成完整的最终顺序。
            你必须遵守以下规则：
            1. 你是唯一的排线决策者，本地程序不会给你规则候选顺序；
            2. 你必须返回完整且唯一的 finalOrderIds，禁止缺单、重复、部分返回；
            3. finalOrderIds 必须原样回传输入里提供的 orderId，不要自行改写成新的编号，不要重排为 1..N，不要输出字符串编号；
            4. 你必须同时返回结构化思考步骤 analysisSteps、逐单原因 perOrderReasons；
            5. 如果你无法可靠完成排线，必须按 failure 结构返回失败原因；
            6. 只允许输出 JSON，不要输出任何额外解释。
            返回格式：
            {
              "success": true,
              "summary": "...",
              "analysisSteps": [{"type":"context_read","title":"...","message":"..."}],
              "finalOrderIds": [1,2,3],
              "perOrderReasons": [{"orderId":1,"reason":"..."}],
              "confidence": 0.92,
              "failure": null
            }
            """;
        String template = promptTemplate == null ? "" : promptTemplate.trim();
        if (template.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n以下是业务补充偏好，请在不违反上述铁律的前提下执行：\n" + template;
    }

    private String buildPlanningUserContext(DispatchRouteAiPlanningRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode taskMeta = root.putObject("taskMeta");
        taskMeta.put("scene", request.scene());
        taskMeta.put("goal", "为配送任务生成完整排序");
        taskMeta.put("strategyMode", request.strategyMode());

        ObjectNode areaContext = root.putObject("areaContext");
        areaContext.put("areaCode", request.areaCode());
        areaContext.put("anchorName", request.anchorName());
        areaContext.put("anchorAddress", request.anchorAddress());

        root.set("historyPreferences", objectMapper.valueToTree(request.historyPreferenceSummary()));

        ObjectNode constraints = root.putObject("constraints");
        constraints.put("mustReturnAllOrders", true);
        constraints.put("allowPartialResult", false);
        constraints.put("allowDuplicateOrders", false);
        constraints.put("mustProvideReasoning", true);
        root.set("validOrderIds", objectMapper.valueToTree(request.points().stream().map(DispatchRoutePoint::orderId).toList()));

        ArrayNode addresses = root.putArray("addresses");
        for (DispatchRoutePoint point : request.points()) {
            ObjectNode node = addresses.addObject();
            node.put("orderId", point.orderId());
            node.put("address", point.addressLabel());
            node.put("clusterName", point.clusterName());
            node.put("buildingName", point.buildingName());
            node.put("roadName", point.roadName());
            node.set("locationTokens", objectMapper.valueToTree(point.locationTokens()));
        }
        return objectMapper.writeValueAsString(root);
    }

    DispatchRouteAiPlanningResult validatePlanningResult(String rawJson, List<DispatchRoutePoint> points) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            boolean success = root.path("success").asBoolean(true);
            if (!success) {
                JsonNode failureNode = root.path("failure");
                String message = failureNode.path("message").asText("");
                String failureCode = failureNode.path("code").asText("MODEL_REPORTED_FAILURE");
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "AI 排线失败(" + failureCode + ")：" + (message.isBlank() ? "模型未返回可用结果" : message)
                );
            }
            List<Long> expectedOrderIds = points.stream().map(DispatchRoutePoint::orderId).toList();
            List<Long> finalOrderIds = readOrderIds(root.path("finalOrderIds"));
            if (!isSameSet(finalOrderIds, expectedOrderIds)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型返回订单集合不完整或存在重复");
            }
            if (finalOrderIds.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型未返回最终顺序");
            }
            List<DispatchRouteAiPlanningResult.DispatchRouteAiStep> analysisSteps = readAnalysisSteps(root.path("analysisSteps"));
            if (analysisSteps.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型未返回思考步骤");
            }
            List<DispatchRouteAiPlanningResult.DispatchRouteAiGroup> groups = readGroups(root.path("groups"));
            List<DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason> perOrderReasons = readOrderReasons(root.path("perOrderReasons"));
            Map<Long, String> reasonMap = new LinkedHashMap<>();
            for (DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason reason : perOrderReasons) {
                reasonMap.put(reason.orderId(), reason.reason());
            }
            List<DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason> normalizedReasons = expectedOrderIds.stream()
                .map(orderId -> new DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason(
                    orderId,
                    reasonMap.getOrDefault(orderId, "AI 根据地址语义和历史偏好生成当前顺序")
                ))
                .toList();
            double confidence = root.path("confidence").asDouble(1.0d);
            if (confidence < MIN_CONFIDENCE) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型置信度过低");
            }
            String summary = root.path("summary").asText("");
            return new DispatchRouteAiPlanningResult(
                true,
                summary.isBlank() ? "AI 已基于地址语义生成完整排线顺序" : summary,
                analysisSteps,
                groups,
                finalOrderIds,
                normalizedReasons,
                confidence,
                null,
                rawJson
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型输出解析失败");
        }
    }

    private String buildUserContext(DispatchRouteAiRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode anchor = root.putObject("anchor");
        anchor.put("name", request.anchorName());
        anchor.put("address", request.anchorAddress());
        root.put("strategyMode", request.strategyMode());
        root.set("ruleCandidateOrderIds", objectMapper.valueToTree(request.ruleCandidateOrderIds()));
        root.set("historyPreferenceSummary", objectMapper.valueToTree(request.historyPreferenceSummary()));
        ObjectNode limit = root.putObject("adjustmentLimit");
        limit.put("maxPositionShift", request.maxPositionShift());
        limit.put("maxChangedOrders", request.maxChangedOrders());
        ArrayNode orders = root.putArray("orders");
        for (DispatchRoutePoint point : request.points()) {
            ObjectNode node = orders.addObject();
            node.put("orderId", point.orderId());
            node.put("address", point.addressLabel());
            node.put("clusterKey", point.clusterName());
            node.put("buildingKey", point.buildingName());
            node.put("roadKey", point.roadName());
            node.put("distanceToAnchor", point.anchorDistance());
            node.put("neighborCount", point.neighborCount());
            node.set("locationTokens", objectMapper.valueToTree(point.locationTokens()));
        }
        return objectMapper.writeValueAsString(root);
    }

    private String extractContent(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        return root.path("choices").path(0).path("message").path("content").asText("{}");
    }

    private String summarizeProviderError(String body) {
        String summary = extractProviderMessage(body);
        if (summary.isBlank()) {
            return "响应体为空";
        }
        String compact = summary.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_PROVIDER_ERROR_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_PROVIDER_ERROR_LENGTH) + "...";
    }

    private String extractProviderMessage(String body) {
        String raw = body == null ? "" : body.trim();
        if (raw.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String nestedError = root.path("error").path("message").asText("");
            if (!nestedError.isBlank()) {
                return nestedError;
            }
            String topLevelMessage = root.path("message").asText("");
            if (!topLevelMessage.isBlank()) {
                return topLevelMessage;
            }
            return root.toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private List<Long> readOrderIds(JsonNode orderIdsNode) {
        List<Long> orderIds = new ArrayList<>();
        if (!orderIdsNode.isArray()) {
            return orderIds;
        }
        for (JsonNode node : orderIdsNode) {
            if (node.canConvertToLong()) {
                orderIds.add(node.longValue());
                continue;
            }
            if (node.isTextual()) {
                String raw = node.asText("").trim();
                if (raw.matches("-?\\d+")) {
                    orderIds.add(Long.parseLong(raw));
                }
            }
        }
        return orderIds;
    }

    private List<DispatchRouteAiPlanningResult.DispatchRouteAiStep> readAnalysisSteps(JsonNode stepsNode) {
        List<DispatchRouteAiPlanningResult.DispatchRouteAiStep> steps = new ArrayList<>();
        if (!stepsNode.isArray()) {
            return steps;
        }
        for (JsonNode node : stepsNode) {
            String type = node.path("type").asText("");
            String title = node.path("title").asText("");
            String message = node.path("message").asText("");
            if (title.isBlank() && message.isBlank()) {
                continue;
            }
            steps.add(new DispatchRouteAiPlanningResult.DispatchRouteAiStep(
                type.isBlank() ? "analysis" : type,
                title.isBlank() ? "AI 思考" : title,
                message.isBlank() ? "AI 已完成当前步骤分析" : message
            ));
        }
        return steps;
    }

    private List<DispatchRouteAiPlanningResult.DispatchRouteAiGroup> readGroups(JsonNode groupsNode) {
        List<DispatchRouteAiPlanningResult.DispatchRouteAiGroup> groups = new ArrayList<>();
        if (!groupsNode.isArray()) {
            return groups;
        }
        for (JsonNode node : groupsNode) {
            String groupName = node.path("groupName").asText("");
            List<Long> orderIds = readOrderIds(node.path("orderIds"));
            if (groupName.isBlank() && orderIds.isEmpty()) {
                continue;
            }
            groups.add(new DispatchRouteAiPlanningResult.DispatchRouteAiGroup(
                groupName.isBlank() ? "未命名分组" : groupName,
                orderIds
            ));
        }
        return groups;
    }

    private List<DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason> readOrderReasons(JsonNode reasonsNode) {
        List<DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason> reasons = new ArrayList<>();
        if (!reasonsNode.isArray()) {
            return reasons;
        }
        for (JsonNode node : reasonsNode) {
            if (!node.path("orderId").canConvertToLong()) {
                continue;
            }
            reasons.add(new DispatchRouteAiPlanningResult.DispatchRouteAiOrderReason(
                node.path("orderId").longValue(),
                node.path("reason").asText("")
            ));
        }
        return reasons;
    }

    private boolean isSameSet(List<Long> candidate, List<Long> fallback) {
        if (candidate.size() != fallback.size()) {
            return false;
        }
        Set<Long> candidateSet = new HashSet<>(candidate);
        Set<Long> fallbackSet = new HashSet<>(fallback);
        return candidateSet.size() == candidate.size() && candidateSet.equals(fallbackSet);
    }

    private boolean exceedsBounds(List<Long> candidate, List<Long> fallback, int maxPositionShift, int maxChangedOrders) {
        int changedOrders = 0;
        for (int i = 0; i < fallback.size(); i++) {
            long orderId = fallback.get(i);
            int newIndex = candidate.indexOf(orderId);
            if (newIndex < 0) {
                return true;
            }
            if (Math.abs(newIndex - i) > maxPositionShift) {
                return true;
            }
            if (newIndex != i) {
                changedOrders++;
            }
        }
        return changedOrders > maxChangedOrders;
    }

    private String trimTrailingSlash(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record DispatchRouteAiRequest(
        boolean aiEnabled,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String promptTemplate,
        String anchorName,
        String anchorAddress,
        String strategyMode,
        List<DispatchRoutePoint> points,
        List<Long> ruleCandidateOrderIds,
        List<String> historyPreferenceSummary,
        int maxPositionShift,
        int maxChangedOrders,
        String defaultReasonSummary
    ) {}

    public record DispatchRouteAiPlanningRequest(
        String scene,
        String areaCode,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String promptTemplate,
        String anchorName,
        String anchorAddress,
        String strategyMode,
        List<DispatchRoutePoint> points,
        List<String> historyPreferenceSummary
    ) {
    }
}
