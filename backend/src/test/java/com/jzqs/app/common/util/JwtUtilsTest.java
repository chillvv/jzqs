package com.jzqs.app.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jzqs.app.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JwtUtilsTest {

    private static final String JWT_SECRET_PROPERTY = "app.jwt.secret";
    private static final String ALLOW_DEFAULT_SECRET_PROPERTY = "app.jwt.allow-default-secret";
    private static final String LEGACY_SECRET = "jzqs-mobile-auth-secret";

    @AfterEach
    void tearDown() {
        System.clearProperty(JWT_SECRET_PROPERTY);
        System.clearProperty(ALLOW_DEFAULT_SECRET_PROPERTY);
        System.clearProperty("spring.profiles.active");
    }

    @Test
    void tokenParsingFailsWhenSecretChanges() {
        System.setProperty(JWT_SECRET_PROPERTY, "first-secret");
        String token = JwtUtils.generateToken(JwtClaims.admin(1L, "OWNER", "管理员"));

        System.setProperty(JWT_SECRET_PROPERTY, "second-secret");

        assertThrows(BusinessException.class, () -> JwtUtils.parseToken(token));
    }

    @Test
    void tokenParsingUsesConfiguredSecret() {
        System.setProperty(JWT_SECRET_PROPERTY, "shared-secret");

        String token = JwtUtils.generateToken(JwtClaims.admin(7L, "OWNER", "管理员"));
        JwtClaims claims = JwtUtils.parseToken(token);

        assertEquals(7L, claims.userId());
        assertEquals("admin", claims.userType());
        assertEquals("OWNER", claims.role());
    }

    @Test
    void tokenParsingRejectsLegacySecretTokens() {
        System.setProperty(JWT_SECRET_PROPERTY, "shared-secret");

        String token = legacyToken(Map.of("userId", 9L, "customerId", 9L, "userType", "customer"));
        assertThrows(BusinessException.class, () -> JwtUtils.parseToken(token));
    }

    @Test
    void tokenRoundTripPreservesEscapedClaimValues() {
        System.setProperty(JWT_SECRET_PROPERTY, "shared-secret");

        String token = JwtUtils.generateToken(
            JwtClaims.builder()
                .userId(12L)
                .riderId(12L)
                .userType("rider")
                .riderName("骑手&A=12")
                .openid("openid=a&b")
                .build()
        );

        JwtClaims claims = JwtUtils.parseToken(token);

        assertEquals(12L, claims.userId());
        assertEquals(12L, claims.riderId());
        assertEquals("骑手&A=12", claims.riderName());
        assertEquals("openid=a&b", claims.openid());
    }

    @Test
    void rejectsWeakConfiguredSecret() {
        System.setProperty(JWT_SECRET_PROPERTY, "short");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> JwtUtils.generateToken(JwtClaims.admin(7L, "OWNER", "管理员"))
        );

        assertEquals("APP_JWT_SECRET 配置过弱", exception.getMessage());
    }

    @Test
    void requiresExplicitSecretWhenDefaultSecretIsNotExplicitlyAllowed() {
        System.clearProperty(JWT_SECRET_PROPERTY);
        System.setProperty("spring.profiles.active", "prod");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> JwtUtils.generateToken(JwtClaims.admin(7L, "OWNER", "管理员"))
        );

        assertEquals("APP_JWT_SECRET 未配置", exception.getMessage());
    }

    @Test
    void fallsBackToDevelopmentSecretWhenExplicitlyAllowed() {
        // 远程实现：非 prod 也不静默回退，必须显式允许默认密钥
        System.clearProperty(JWT_SECRET_PROPERTY);
        System.setProperty(ALLOW_DEFAULT_SECRET_PROPERTY, "true");

        String token = JwtUtils.generateToken(JwtClaims.admin(7L, "OWNER", "管理员"));
        JwtClaims claims = JwtUtils.parseToken(token);

        assertEquals(7L, claims.userId());
        assertEquals("OWNER", claims.role());
    }

    private String legacyToken(Map<String, Object> claims) {
        long expireAt = Instant.now().getEpochSecond() + 7L * 24 * 3600;
        long issuedAt = Instant.now().getEpochSecond();
        Map<String, Object> payloadClaims = new LinkedHashMap<>(claims);
        payloadClaims.put("exp", expireAt);
        payloadClaims.put("iat", issuedAt);

        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, Object> entry : payloadClaims.entrySet()) {
            if (payload.length() > 0) {
                payload.append("&");
            }
            payload.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String payloadStr = payload.toString();
        String signature = signLegacy(payloadStr);
        String raw = payloadStr + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String signLegacy(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(LEGACY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("legacy token sign failed", ex);
        }
    }
}
