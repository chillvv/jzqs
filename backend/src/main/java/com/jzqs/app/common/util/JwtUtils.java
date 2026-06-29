package com.jzqs.app.common.util;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtUtils {
    private static final long EXPIRE_SECONDS = 7L * 24 * 3600;
    private static final int MIN_SECRET_LENGTH = 8;
    private static final String DEV_FALLBACK_SECRET = "dev-local-jwt-secret-change-me-2026";
    private static final String ALLOW_DEFAULT_SECRET_PROPERTY = "app.jwt.allow-default-secret";
    private static final String ACTIVE_PROFILE_PROPERTY = "spring.profiles.active";
    private static final String ACTIVE_PROFILE_ENV = "SPRING_PROFILES_ACTIVE";

    private JwtUtils() {
    }

    public static String generateToken(JwtClaims claims) {
        long expireAt = Instant.now().getEpochSecond() + EXPIRE_SECONDS;
        long issuedAt = Instant.now().getEpochSecond();
        JwtClaims payloadClaims = JwtClaims.builder()
            .userId(claims == null ? null : claims.userId())
            .customerId(claims == null ? null : claims.customerId())
            .riderId(claims == null ? null : claims.riderId())
            .userType(claims == null ? null : claims.userType())
            .role(claims == null ? null : claims.role())
            .displayName(claims == null ? null : claims.displayName())
            .riderName(claims == null ? null : claims.riderName())
            .phone(claims == null ? null : claims.phone())
            .openid(claims == null ? null : claims.openid())
            .exp(expireAt)
            .iat(issuedAt)
            .build();

        StringBuilder payload = new StringBuilder();
        for (JwtClaims.PayloadEntry entry : payloadClaims.toPayloadEntries()) {
            if (payload.length() > 0) {
                payload.append("&");
            }
            payload.append(encode(entry.key())).append("=").append(encode(entry.value()));
        }
        String payloadStr = payload.toString();
        String signature = sign(payloadStr);
        String raw = payloadStr + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static JwtClaims parseToken(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
            }
            
            String payloadStr = parts[0];
            String signature = parts[1];
            
            // 验证签名
            if (!matchesSignature(payloadStr, signature)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
            }
            
            JwtClaims.Builder builder = JwtClaims.builder();
            Long expireAt = null;
            Long issuedAt = null;
            String[] pairs = payloadStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String key = decode(kv[0]);
                    String value = decode(kv[1]);
                    Long parsedLong = parseLongValue(value);
                    switch (key) {
                        case "userId" -> builder.userId(parsedLong);
                        case "customerId" -> builder.customerId(parsedLong);
                        case "riderId" -> builder.riderId(parsedLong);
                        case "userType" -> builder.userType(value);
                        case "role" -> builder.role(value);
                        case "displayName" -> builder.displayName(value);
                        case "riderName" -> builder.riderName(value);
                        case "phone" -> builder.phone(value);
                        case "openid" -> builder.openid(value);
                        case "exp" -> expireAt = parsedLong;
                        case "iat" -> issuedAt = parsedLong;
                        default -> {
                        }
                    }
                }
            }

            if (expireAt == null || Instant.now().getEpochSecond() > expireAt) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已过期，请重新登录");
            }

            return builder
                .exp(expireAt)
                .iat(issuedAt)
                .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
        }
    }

    /**
     * 解析 customerId（旧版兼容方法）
     * @deprecated 使用 parseToken(String token) 替代
     */
    @Deprecated
    public static long parseCustomerId(String token) {
        JwtClaims claims = parseToken(token);
        Long userId = claims.effectiveUserId();
        if (userId != null) {
            return userId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
    }

    private static Long parseLongValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String sign(String data) {
        return signWithSecret(data, secret());
    }

    private static boolean matchesSignature(String data, String signature) {
        if (signature == null) {
            return false;
        }
        return signatureMatches(data, signature, secret());
    }

    private static boolean signatureMatches(String data, String signature, String secret) {
        String expected = signWithSecret(data, secret);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String signWithSecret(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("token sign failed", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String secret() {
        String configured = readPropertyOrEnv("app.jwt.secret", "APP_JWT_SECRET");
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            if (trimmed.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("APP_JWT_SECRET 配置过弱");
            }
            return trimmed;
        }
        if (shouldAllowDefaultSecret()) {
            return DEV_FALLBACK_SECRET;
        }
        throw new IllegalStateException("APP_JWT_SECRET 未配置");
    }

    private static boolean shouldAllowDefaultSecret() {
        String allowDefault = readPropertyOrEnv(ALLOW_DEFAULT_SECRET_PROPERTY, "APP_JWT_ALLOW_DEFAULT_SECRET");
        if ("true".equalsIgnoreCase(allowDefault)) {
            return true;
        }
        String activeProfiles = readPropertyOrEnv(ACTIVE_PROFILE_PROPERTY, ACTIVE_PROFILE_ENV);
        return activeProfiles == null || !containsProdProfile(activeProfiles);
    }

    private static String readPropertyOrEnv(String propertyName, String envName) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(envName);
        }
        return configured;
    }

    private static boolean containsProdProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            if ("prod".equalsIgnoreCase(profile == null ? "" : profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
