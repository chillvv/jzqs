package com.jzqs.app.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 统一认证过滤器
 * 拦截所有请求（除白名单），验证 JWT token
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */
@Component
@ConditionalOnProperty(name = "app.auth.filter.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class AuthFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String GENERIC_UNAUTHORIZED_MESSAGE = "无权访问";
    private static final int MIN_INTERNAL_TOKEN_LENGTH = 8;

    // 只放行登录、文档、健康检查和公开顾客首页接口
    private static final List<String> ALWAYS_ALLOW_PREFIXES = List.of(
        "/api/auth/login",
        "/api/auth/bind-phone",
        "/api/auth/register-phone",
        "/api/auth/phone-login",
        "/api/auth/verify",
        "/api/auth/logout",
        "/api/mobile/auth/wx-login",
        "/api/mobile/auth/phone-login",
        "/api/mobile/auth/register",
        "/api/mobile/auth/bind-phone",
        "/api/mobile/auth/dev-phone",
        "/api/mobile/auth/complete-profile",
        "/api/mobile/rider-auth/wx-login",
        "/api/mobile/rider-auth/bind-phone",
        "/api/mobile/rider-auth/verify-token",
        "/api/admin/auth/login",
        "/uploads/",
        "/ws/realtime",
        "/actuator/",
        "/swagger-ui/",
        "/v3/api-docs/",
        "/error"
    );

    // 顾客首页和菜单在当前版本允许游客访问
    private static final List<String> GUEST_ACCESS_PREFIXES = List.of(
        "/api/mobile/customer/home",
        "/api/mobile/customer/menu/",
        "/api/mobile/customer/menus/"
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

        if (path.startsWith("/api/internal/")) {
            try {
                verifyInternalToken(httpRequest);
                chain.doFilter(request, response);
            } catch (IllegalStateException ex) {
                log.error("内部接口令牌未配置");
                writeUnauthorized(httpResponse, GENERIC_UNAUTHORIZED_MESSAGE);
            } catch (BusinessException ex) {
                writeUnauthorized(httpResponse, GENERIC_UNAUTHORIZED_MESSAGE);
            }
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
            JwtClaims claims = JwtUtils.parseToken(token);

            if (path.startsWith("/api/admin/") && !isAdmin(claims)) {
                writeUnauthorized(httpResponse, GENERIC_UNAUTHORIZED_MESSAGE);
                return;
            }
            
            // 将用户信息注入请求上下文
            Long userId = extractUserId(claims);
            Long customerId = claims.customerId();
            String userType = claims.userType();
            Long riderId = claims.riderId();
            String riderName = claims.riderName();
            String role = claims.role();
            String displayName = claims.displayName();
            
            if (userId != null) {
                httpRequest.setAttribute("userId", userId);
            }
            if (customerId != null) {
                httpRequest.setAttribute("customerId", customerId);
            }
            if (userType != null) {
                httpRequest.setAttribute("userType", userType);
            }
            if (riderId != null) {
                httpRequest.setAttribute("riderId", riderId);
            }
            if (riderName != null) {
                httpRequest.setAttribute("riderName", riderName);
            }
            if (role != null) {
                httpRequest.setAttribute("adminRole", role);
            }
            if (displayName != null) {
                httpRequest.setAttribute("adminDisplayName", displayName);
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
        return false;
    }

    String internalApiToken() {
        String configured = System.getProperty("app.internal.api.token");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("APP_INTERNAL_API_TOKEN");
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("APP_INTERNAL_API_TOKEN 未配置");
        }
        String trimmed = configured.trim();
        if (trimmed.length() < MIN_INTERNAL_TOKEN_LENGTH) {
            throw new IllegalStateException("APP_INTERNAL_API_TOKEN 配置过弱");
        }
        return trimmed;
    }

    private void verifyInternalToken(HttpServletRequest request) {
        String provided = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (provided == null || provided.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少内部调用凭证");
        }
        String expected = internalApiToken();
        if (!expected.equals(provided.trim())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "内部调用凭证无效");
        }
    }

    private boolean isAdmin(JwtClaims claims) {
        return claims.isAdmin();
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
    private Long extractUserId(JwtClaims claims) {
        return claims.effectiveUserId();
    }
}
