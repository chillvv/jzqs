package com.jzqs.app.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 统一认证过滤器
 * 拦截所有请求（除白名单），验证 JWT token
 * 
 * 注意：当前已禁用，待前端完全迁移到新认证系统后再启用
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */
// @Component  // 暂时禁用，避免影响现有系统
@Order(1)
public class AuthFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    // 统一认证与后台接口始终放行，避免登录链路被过滤器打断
    private static final List<String> ALWAYS_ALLOW_PREFIXES = List.of(
        "/api/auth/login",
        "/api/auth/bind-phone",
        "/api/auth/register-phone",
        "/api/auth/phone-login",
        "/api/auth/verify",
        "/api/auth/logout",
        "/api/mobile/auth/",  // 旧版认证接口（兼容）
        "/api/mobile/rider-auth/",  // 旧版骑手认证接口（兼容）
        "/api/admin/",  // 管理后台接口（另有认证机制）
        "/actuator/",  // Spring Boot Actuator
        "/swagger-ui/",  // Swagger UI
        "/v3/api-docs/"  // OpenAPI 文档
    );

    // 顾客首页和菜单在当前版本允许游客访问
    private static final List<String> GUEST_ACCESS_PREFIXES = List.of(
        "/api/mobile/customer/home",
        "/api/mobile/customer/menu/",
        "/api/mobile/customer/menus/"
    );

    // 这些骑手路径仍依赖 riderName 或请求体中的显式骑手信息，暂不纳入统一过滤
    private static final List<String> DEFERRED_PROTECTED_PREFIXES = List.of(
        "/api/mobile/rider/",
        "/api/rider/"
    );

    private final ObjectMapper objectMapper;

    public AuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // OPTIONS 请求直接放行（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 白名单放行
        if (isWhiteListed(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 提取 Authorization header
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(httpResponse, "登录态已失效，请重新登录");
            return;
        }

        // 解析 JWT
        String token = authorization.substring(7);
        try {
            Map<String, Object> claims = JwtUtils.parseToken(token);
            
            // 将用户信息注入请求上下文
            Long userId = extractUserId(claims);
            String userType = (String) claims.get("userType");
            
            if (userId != null) {
                httpRequest.setAttribute("userId", userId);
            }
            if (userType != null) {
                httpRequest.setAttribute("userType", userType);
            }
            
            // 放行
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            log.warn("JWT 验证失败：{}", e.getMessage());
            writeUnauthorized(httpResponse, e.getMessage());
        } catch (Exception e) {
            log.error("JWT 验证异常", e);
            writeUnauthorized(httpResponse, "登录态已失效，请重新登录");
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhiteListed(String path) {
        for (String prefix : ALWAYS_ALLOW_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : GUEST_ACCESS_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : DEFERRED_PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 401 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        ApiResponse<Object> apiResponse = ApiResponse.failure("UNAUTHORIZED", message);
        String json = objectMapper.writeValueAsString(apiResponse);
        
        response.getWriter().write(json);
    }

    /**
     * 从 claims 中提取 userId
     */
    private Long extractUserId(Map<String, Object> claims) {
        Object userId = claims.get("userId");
        if (userId == null) {
            userId = claims.get("riderId");
        }
        if (userId == null) {
            userId = claims.get("customerId");
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }
}
