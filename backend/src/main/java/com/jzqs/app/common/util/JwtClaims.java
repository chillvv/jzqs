package com.jzqs.app.common.util;

import java.util.ArrayList;
import java.util.List;

public record JwtClaims(
    Long userId,
    Long customerId,
    Long riderId,
    String userType,
    String role,
    String displayName,
    String riderName,
    String phone,
    String openid,
    Long exp,
    Long iat
) {
    public JwtClaims {
        userType = normalize(userType);
        role = normalize(role);
        displayName = normalize(displayName);
        riderName = normalize(riderName);
        phone = normalize(phone);
        openid = normalize(openid);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JwtClaims admin(long userId, String role, String displayName) {
        return builder()
            .userId(userId)
            .userType("admin")
            .role(role)
            .displayName(displayName)
            .build();
    }

    public static JwtClaims customer(long customerId) {
        return customer(customerId, null);
    }

    public static JwtClaims customer(long customerId, String openid) {
        return builder()
            .userId(customerId)
            .customerId(customerId)
            .userType("customer")
            .openid(openid)
            .build();
    }

    public static JwtClaims rider(long riderId, String riderName, String phone, String openid) {
        return builder()
            .userId(riderId)
            .riderId(riderId)
            .userType("rider")
            .riderName(riderName)
            .phone(phone)
            .openid(openid)
            .build();
    }

    public Long effectiveUserId() {
        if (userId != null) {
            return userId;
        }
        if (riderId != null) {
            return riderId;
        }
        return customerId;
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(userType) || (role != null && !role.isBlank());
    }

    List<PayloadEntry> toPayloadEntries() {
        List<PayloadEntry> payload = new ArrayList<>();
        add(payload, "userId", userId);
        add(payload, "customerId", customerId);
        add(payload, "riderId", riderId);
        add(payload, "userType", userType);
        add(payload, "role", role);
        add(payload, "displayName", displayName);
        add(payload, "riderName", riderName);
        add(payload, "phone", phone);
        add(payload, "openid", openid);
        add(payload, "exp", exp);
        add(payload, "iat", iat);
        return List.copyOf(payload);
    }

    private static void add(List<PayloadEntry> payload, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        payload.add(new PayloadEntry(key, String.valueOf(value)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private Long userId;
        private Long customerId;
        private Long riderId;
        private String userType;
        private String role;
        private String displayName;
        private String riderName;
        private String phone;
        private String openid;
        private Long exp;
        private Long iat;

        private Builder() {
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder riderId(Long riderId) {
            this.riderId = riderId;
            return this;
        }

        public Builder userType(String userType) {
            this.userType = userType;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder riderName(String riderName) {
            this.riderName = riderName;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder openid(String openid) {
            this.openid = openid;
            return this;
        }

        public Builder exp(Long exp) {
            this.exp = exp;
            return this;
        }

        public Builder iat(Long iat) {
            this.iat = iat;
            return this;
        }

        public JwtClaims build() {
            return new JwtClaims(userId, customerId, riderId, userType, role, displayName, riderName, phone, openid, exp, iat);
        }
    }

    record PayloadEntry(String key, String value) {
    }
}
