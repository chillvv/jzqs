package com.jzqs.app.common.security;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class AdminRequestContextSupport {
    public static final String USER_ID_ATTR = "userId";
    public static final String USER_TYPE_ATTR = "userType";
    public static final String ADMIN_ROLE_ATTR = "adminRole";
    public static final String ADMIN_DISPLAY_NAME_ATTR = "adminDisplayName";

    private AdminRequestContextSupport() {
    }

    public static AdminRequestContext requireAdmin() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        Object userType = request.getAttribute(USER_TYPE_ATTR);
        if (!"admin".equalsIgnoreCase(stringValue(userType))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台权限");
        }
        Long userId = longValue(request.getAttribute(USER_ID_ATTR));
        String role = stringValue(request.getAttribute(ADMIN_ROLE_ATTR));
        String displayName = stringValue(request.getAttribute(ADMIN_DISPLAY_NAME_ATTR));
        return new AdminRequestContext(userId, role, displayName);
    }

    public static String requireOperatorName() {
        return requireAdmin().operatorName();
    }

    public static String currentRequestPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "";
        }
        return servletRequestAttributes.getRequest().getRequestURI();
    }

    private static Long longValue(Object value) {
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

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
