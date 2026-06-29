package com.jzqs.app.common.aop.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.security.AdminRequestContext;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

final class AopRequestKeySupport {
    private AopRequestKeySupport() {
    }

    static String rateLimitKey(String actionKey) {
        AdminRequestContext admin = AdminRequestContextSupport.requireAdmin();
        return actionKey + "|" + admin.userId() + "|" + AdminRequestContextSupport.currentRequestPath();
    }

    static String idempotencyKey(String actionKey, ProceedingJoinPoint joinPoint, ObjectMapper objectMapper, boolean includeBody) {
        AdminRequestContext admin = AdminRequestContextSupport.requireAdmin();
        StringBuilder builder = new StringBuilder()
            .append(actionKey)
            .append("|")
            .append(admin.userId())
            .append("|")
            .append(AdminRequestContextSupport.currentRequestPath());
        if (includeBody) {
            builder.append("|").append(bodyDigest(joinPoint.getArgs(), objectMapper));
        }
        return builder.toString();
    }

    private static String bodyDigest(Object[] args, ObjectMapper objectMapper) {
        List<Object> serializableArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof BindingResult
                || arg instanceof MultipartFile) {
                continue;
            }
            serializableArgs.add(arg);
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(serializableArgs);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(bytes));
        } catch (JsonProcessingException ex) {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(String.valueOf(serializableArgs).getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("request digest failed", ex);
        }
    }
}
