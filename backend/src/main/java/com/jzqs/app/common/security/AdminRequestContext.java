package com.jzqs.app.common.security;

public record AdminRequestContext(
    Long userId,
    String role,
    String displayName
) {
    public String operatorName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (userId != null) {
            return "管理员#" + userId;
        }
        if (role != null && !role.isBlank()) {
            return role;
        }
        return "商家后台";
    }
}
