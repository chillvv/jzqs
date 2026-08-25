package com.jzqs.app.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import com.jzqs.app.common.aop.annotation.AuditAction;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 后台操作审计过滤器
 * 拦截 /api/admin/** 下所有写操作(POST/PUT/DELETE/PATCH)，自动记录操作人、操作内容与结果到 admin_operation_logs 表。
 * 覆盖范围不依赖 @AuditAction 注解——未加注解的接口同样会记录，保证任何后台操作都不遗漏；
 * @AuditAction 注解仅用于提供更友好的 module/action 标签。
 * 登录接口(/api/admin/auth/login)在白名单中，同样会被记录为"登录尝试"，operator 为请求中的手机号。
 */
@Component
@Order(2)
public class AdminOperationLogFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogFilter.class);

    private static final Set<String> AUDITED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final int MAX_SUMMARY_LENGTH = 800;
    private static final List<String> SENSITIVE_FIELDS = List.of("password", "oldPassword", "newPassword");
    /** 操作对象路径解析：客户/骑手/后台账号/订单/售后单，用于在请求执行前快照对象信息，删除后仍能追溯 */
    private static final Pattern CUSTOMER_ID_PATH = Pattern.compile("/customers/(\\d+)");
    private static final Pattern RIDER_ID_PATH = Pattern.compile("/riders/(\\d+)");
    private static final Pattern ADMIN_USER_ID_PATH = Pattern.compile("/users/(\\d+)");
    private static final Pattern ORDER_ID_PATH = Pattern.compile("/orders/(\\d+)");
    private static final Pattern AFTERSALE_ID_PATH = Pattern.compile("/aftersales/(\\d+)");

    /** 请求执行前捕获的对象快照（JSON 字符串），存入 request attribute 供日志写入时使用 */
    private static final String TARGET_SNAPSHOT_ATTR = "auditTargetSnapshot";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminOperationLogFilter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        if (!path.startsWith("/api/admin/") || !AUDITED_METHODS.contains(method.toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        // 在业务代码执行前捕获操作对象快照（姓名/手机号等），
        // 删除类操作执行后记录已消失，此时快照能保证日志仍可追溯"删了什么"
        captureTargetSnapshot(wrappedRequest);
        try {
            chain.doFilter(wrappedRequest, httpResponse);
        } finally {
            saveLog(wrappedRequest, httpResponse, startedAt);
        }
    }

    private void saveLog(ContentCachingRequestWrapper request, HttpServletResponse response, long startedAt) {
        try {
            String path = request.getRequestURI();
            boolean isAuthEndpoint = path.startsWith("/api/admin/auth/");
            String requestBody = extractAndSanitizeBody(request);
            // 合并请求执行前捕获的对象快照（姓名/手机号/订单归属客户等），
            // 删除类操作后对象已不存在，快照可保证日志仍显示"删了什么"
            Object snapshotAttr = request.getAttribute(TARGET_SNAPSHOT_ATTR);
            if (snapshotAttr instanceof String snapshotJson && snapshotJson != null && !snapshotJson.isBlank()) {
                requestBody = mergeSnapshotIntoBody(requestBody, snapshotJson);
            }
            // 把操作对象（客户/骑手/后台账号）当前手机号快照进日志参数，
            // 便于对象被删除后按手机号重建时，旧日志仍能通过手机号反查当前姓名
            requestBody = enrichTargetPhone(path, requestBody);

            Long operatorId = longValue(request.getAttribute("userId"));
            String operatorRole = stringValue(request.getAttribute("adminRole"));
            String operatorName = stringValue(request.getAttribute("adminDisplayName"));
            String operatorPhone = null;
            int status = response.getStatus();
            if (isAuthEndpoint) {
                // 登录/改密尝试：身份未知，用请求体中的手机号标识
                operatorPhone = extractPhoneFromJson(requestBody);
                operatorName = operatorPhone;
                operatorId = null;
                // 登录成功时用手机号反查姓名，让操作人显示人名而非手机号
                if (operatorPhone != null && status < 400 && path.endsWith("/login")) {
                    String displayName = queryDisplayNameByPhone(operatorPhone);
                    if (displayName != null) {
                        operatorName = displayName;
                    }
                }
            } else if (operatorId != null) {
                operatorPhone = queryPhone(operatorId);
            }

            String[] moduleAction = resolveModuleAction(request);
            String result = status < 400 ? "SUCCESS" : "FAILED";
            String errorMessage = status < 400 ? null : "HTTP " + status;

            jdbcTemplate.update(
                """
                    INSERT INTO admin_operation_logs
                        (operator_id, operator_name, operator_phone, operator_role, module, action,
                         http_method, request_path, request_summary, status, error_message, duration_ms, client_ip, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                operatorId,
                operatorName,
                operatorPhone,
                operatorRole,
                moduleAction[0],
                moduleAction[1],
                request.getMethod().toUpperCase(),
                buildRequestPath(request),
                requestBody,
                result,
                errorMessage,
                System.currentTimeMillis() - startedAt,
                resolveClientIp(request),
                // MySQL 容器时区为 UTC，显式写入 JVM 时间(Asia/Shanghai)避免差8小时
                LocalDateTime.now()
            );
        } catch (Exception ex) {
            // 审计失败绝不影响主流程
            log.warn("后台操作审计日志写入失败 path={}", request.getRequestURI(), ex);
        }
    }

    private String extractAndSanitizeBody(ContentCachingRequestWrapper request) {
        try {
            String contentType = request.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("multipart/")) {
                return "(文件上传)";
            }
            byte[] content = request.getContentAsByteArray();
            if (content == null || content.length == 0) {
                return null;
            }
            String body = new String(content, StandardCharsets.UTF_8).trim();
            if (body.isEmpty()) {
                return null;
            }
            for (String field : SENSITIVE_FIELDS) {
                body = body.replaceAll("(\"" + field + "\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
            }
            if (body.length() > MAX_SUMMARY_LENGTH) {
                body = body.substring(0, MAX_SUMMARY_LENGTH) + "...(截断)";
            }
            return body;
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractPhoneFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode phone = node.get("phone");
            return phone != null && !phone.isNull() ? phone.asText() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 在业务代码执行前捕获操作对象信息快照，存入 request attribute。
     * 覆盖：客户/骑手/后台账号/订单/售后单；订单与售后单额外带出所属客户姓名。
     * 删除类操作执行后原记录消失，快照能让日志在对象删除后仍显示"删的是什么"。
     */
    private void captureTargetSnapshot(HttpServletRequest request) {
        try {
            String path = request.getRequestURI();
            if (path == null || path.isBlank()) {
                return;
            }
            String pathOnly = path.split("\\?", 2)[0];
            ObjectNode snapshot = objectMapper.createObjectNode();
            Matcher matcher;
            if ((matcher = CUSTOMER_ID_PATH.matcher(pathOnly)).find()) {
                snapshot.put("targetType", "CUSTOMER");
                snapshot.put("targetId", Long.parseLong(matcher.group(1)));
                querySingleRowInto(snapshot, "SELECT name, phone FROM customers WHERE id = ?", matcher.group(1), "name", "phone");
            } else if ((matcher = RIDER_ID_PATH.matcher(pathOnly)).find()) {
                snapshot.put("targetType", "RIDER");
                snapshot.put("targetId", Long.parseLong(matcher.group(1)));
                querySingleRowInto(snapshot, "SELECT rider_name, phone FROM rider_profiles WHERE id = ?", matcher.group(1), "rider_name", "phone");
            } else if ((matcher = ADMIN_USER_ID_PATH.matcher(pathOnly)).find()) {
                snapshot.put("targetType", "ADMIN_USER");
                snapshot.put("targetId", Long.parseLong(matcher.group(1)));
                querySingleRowInto(snapshot, "SELECT display_name, phone FROM users WHERE id = ?", matcher.group(1), "display_name", "phone");
            } else if ((matcher = ORDER_ID_PATH.matcher(pathOnly)).find()) {
                snapshot.put("targetType", "ORDER");
                snapshot.put("targetId", Long.parseLong(matcher.group(1)));
                // 订单关联客户：删除订单后仍能显示"删的谁的订单"（orderId 是 meal_slot_orders.id）
                querySingleRowInto(
                    snapshot,
                    """
                        SELECT c.name AS customer_name, c.phone AS customer_phone
                        FROM meal_slot_orders mso
                        JOIN daily_orders doo ON doo.id = mso.daily_order_id
                        JOIN customers c ON c.id = doo.customer_id
                        WHERE mso.id = ?
                        """,
                    matcher.group(1),
                    "customer_name",
                    "customer_phone"
                );
            } else if ((matcher = AFTERSALE_ID_PATH.matcher(pathOnly)).find()) {
                snapshot.put("targetType", "AFTERSALE");
                snapshot.put("targetId", Long.parseLong(matcher.group(1)));
                // 售后单关联客户：删除售后单后仍能显示客户
                querySingleRowInto(
                    snapshot,
                    """
                        SELECT c.name AS customer_name, c.phone AS customer_phone
                        FROM aftersale_cases a
                        JOIN customers c ON c.id = a.customer_id
                        WHERE a.id = ?
                        """,
                    matcher.group(1),
                    "customer_name",
                    "customer_phone"
                );
            }
            if (snapshot.has("targetType")) {
                request.setAttribute(TARGET_SNAPSHOT_ATTR, objectMapper.writeValueAsString(snapshot));
            }
        } catch (Exception ex) {
            // 快照失败不影响主流程
            log.debug("后台操作日志对象快照捕获失败 path={}", request.getRequestURI(), ex);
        }
    }

    /** 查询一行并把指定列写入快照 JSON；查不到时只保留 targetType/targetId（对象已删除且无快照） */
    private void querySingleRowInto(ObjectNode snapshot, String sql, String id, String nameColumn, String phoneColumn) {
        try {
            long parsedId = Long.parseLong(id);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parsedId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                Object nameValue = row.get(nameColumn);
                Object phoneValue = row.get(phoneColumn);
                snapshot.put(nameColumn, nameValue == null ? "" : String.valueOf(nameValue));
                snapshot.put(phoneColumn, phoneValue == null ? "" : String.valueOf(phoneValue));
            }
        } catch (Exception ex) {
            log.debug("操作日志对象快照查询失败 sql={} id={}", sql, id, ex);
        }
    }

    /** 把快照 JSON 合并进请求体 JSON：快照放到 _target 字段，展示端优先使用快照信息 */
    private String mergeSnapshotIntoBody(String body, String snapshotJson) {
        try {
            ObjectNode bodyNode = objectMapper.createObjectNode();
            if (body != null && body.trim().startsWith("{")) {
                JsonNode parsed = objectMapper.readTree(body);
                if (parsed instanceof ObjectNode existing) {
                    bodyNode = existing;
                }
            }
            bodyNode.set("_target", objectMapper.readTree(snapshotJson));
            return objectMapper.writeValueAsString(bodyNode);
        } catch (Exception ex) {
            return body;
        }
    }

    /**
     * 请求体参数补充操作对象的手机号快照：
     * 命中 /customers/{id}、/riders/{id}、/users/{id} 时，查询该对象当前手机号并入参。
     * 这样对象被删除后按手机号重建时，操作日志仍能显示当前姓名而非"已删除"。
     */
    private String enrichTargetPhone(String path, String body) {
        if (path == null || path.isBlank()) {
            return body;
        }
        String pathOnly = path.split("\\?", 2)[0];
        String phone = null;
        Matcher matcher;
        if ((matcher = CUSTOMER_ID_PATH.matcher(pathOnly)).find()) {
            phone = querySingleString("SELECT phone FROM customers WHERE id = ?", matcher.group(1));
        } else if ((matcher = RIDER_ID_PATH.matcher(pathOnly)).find()) {
            phone = querySingleString("SELECT phone FROM rider_profiles WHERE id = ?", matcher.group(1));
        } else if ((matcher = ADMIN_USER_ID_PATH.matcher(pathOnly)).find()) {
            phone = querySingleString("SELECT phone FROM users WHERE id = ?", matcher.group(1));
        }
        if (phone == null || phone.isBlank()) {
            return body;
        }
        try {
            if (body != null && body.trim().startsWith("{")) {
                JsonNode node = objectMapper.readTree(body);
                if (node instanceof ObjectNode objectNode && !objectNode.has("phone")) {
                    objectNode.put("phone", phone.trim());
                    return objectMapper.writeValueAsString(objectNode);
                }
                return body;
            }
            return "{\"phone\":\"" + phone.trim() + "\"}";
        } catch (Exception ex) {
            return body;
        }
    }

    private String querySingleString(String sql, String id) {
        try {
            long parsedId = Long.parseLong(id);
            List<String> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), parsedId);
            return values.isEmpty() ? null : values.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private String queryPhone(long userId) {
        try {
            List<String> phones = jdbcTemplate.query(
                "SELECT phone FROM users WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                userId
            );
            return phones.isEmpty() ? null : phones.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private String queryDisplayNameByPhone(String phone) {
        try {
            List<String> names = jdbcTemplate.query(
                "SELECT display_name FROM users WHERE phone = ?",
                (rs, rowNum) -> rs.getString(1),
                phone
            );
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 解析业务模块与动作：优先取 @AuditAction 注解；未标注的接口从请求路径推导，保证覆盖所有操作。
     */
    private String[] resolveModuleAction(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            AuditAction audit = handlerMethod.getMethodAnnotation(AuditAction.class);
            if (audit == null) {
                audit = handlerMethod.getBeanType().getAnnotation(AuditAction.class);
            }
            if (audit != null) {
                return new String[]{audit.module(), audit.action()};
            }
        }
        String[] segments = request.getRequestURI().split("/");
        String module = segments.length > 3 ? segments[3].toUpperCase() : "ADMIN";
        String action = "";
        for (int i = segments.length - 1; i >= 4; i--) {
            String segment = segments[i];
            if (!segment.isBlank() && !segment.matches("\\d+")) {
                action = segment.toUpperCase();
                break;
            }
        }
        if (action.isEmpty()) {
            action = request.getMethod().toUpperCase();
        } else {
            action = request.getMethod().toUpperCase() + "_" + action;
        }
        return new String[]{module, action};
    }

    private String buildRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            path = path + "?" + (query.length() > 200 ? query.substring(0, 200) : query);
        }
        return path;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long longValue(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
