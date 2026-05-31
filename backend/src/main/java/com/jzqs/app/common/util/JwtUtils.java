package com.jzqs.app.common.util;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtUtils {
    private static final String SECRET = "jzqs-mobile-auth-secret";
    private static final long EXPIRE_SECONDS = 7L * 24 * 3600;

    private JwtUtils() {
    }

    /**
     * 生成 JWT token（新版，支持任意 claims）
     * @param claims 包含 userId, userType, openid 等字段
     */
    public static String generateToken(Map<String, Object> claims) {
        long expireAt = Instant.now().getEpochSecond() + EXPIRE_SECONDS;
        long issuedAt = Instant.now().getEpochSecond();
        
        // 构建 payload: key1=value1&key2=value2&exp=xxx&iat=xxx
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (payload.length() > 0) {
                payload.append("&");
            }
            payload.append(entry.getKey()).append("=").append(entry.getValue());
        }
        payload.append("&exp=").append(expireAt);
        payload.append("&iat=").append(issuedAt);
        
        String payloadStr = payload.toString();
        String signature = sign(payloadStr);
        String raw = payloadStr + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析 JWT token，返回 claims
     */
    public static Map<String, Object> parseToken(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
            }
            
            String payloadStr = parts[0];
            String signature = parts[1];
            
            // 验证签名
            String expected = sign(payloadStr);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
            }
            
            // 解析 payload
            Map<String, Object> claims = new LinkedHashMap<>();
            String[] pairs = payloadStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0];
                    String value = kv[1];
                    
                    // 尝试转换为数字
                    if (key.equals("userId") || key.equals("riderId") || key.equals("customerId")) {
                        try {
                            claims.put(key, Long.parseLong(value));
                        } catch (NumberFormatException e) {
                            claims.put(key, value);
                        }
                    } else if (key.equals("exp") || key.equals("iat")) {
                        claims.put(key, Long.parseLong(value));
                    } else {
                        claims.put(key, value);
                    }
                }
            }
            
            // 检查过期时间
            Long expireAt = (Long) claims.get("exp");
            if (expireAt == null || Instant.now().getEpochSecond() > expireAt) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已过期，请重新登录");
            }
            
            return claims;
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
        Map<String, Object> claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId == null) {
            userId = claims.get("customerId");
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录");
    }

    private static String sign(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("token sign failed", ex);
        }
    }
}
