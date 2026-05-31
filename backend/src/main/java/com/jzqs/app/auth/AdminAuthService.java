package com.jzqs.app.auth;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.common.util.PasswordUtils;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final JdbcTemplate jdbcTemplate;

    public AdminAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminAuthLoginResponse login(String phone, String password) {
        AdminUserRecord user = requireEnabledAdminUser(phone);
        if (!PasswordUtils.verify(password, user.phone(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.id());
        claims.put("userType", "admin");
        claims.put("role", user.role());
        String token = JwtUtils.generateToken(claims);
        return new AdminAuthLoginResponse(token, user.id(), user.displayName(), user.phone(), user.role());
    }

    @Transactional(readOnly = true)
    public AdminAuthProfileResponse me(String token) {
        Map<String, Object> claims = JwtUtils.parseToken(token);
        long userId = extractUserId(claims);
        AdminUserRecord user = requireEnabledAdminUserById(userId);
        return new AdminAuthProfileResponse(user.id(), user.displayName(), user.phone(), user.role());
    }

    @Transactional
    public Map<String, Object> changePassword(String token, String oldPassword, String newPassword) {
        Map<String, Object> claims = JwtUtils.parseToken(token);
        long userId = extractUserId(claims);
        AdminUserRecord user = requireEnabledAdminUserById(userId);
        if (!PasswordUtils.verify(oldPassword, user.phone(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "旧密码错误");
        }
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?",
            PasswordUtils.hash(newPassword, user.phone()),
            Timestamp.valueOf(LocalDateTime.now()),
            user.id()
        );
        return Map.of("status", "UPDATED");
    }

    public Map<String, Object> logout(String token) {
        JwtUtils.parseToken(token);
        return Map.of("status", "LOGGED_OUT");
    }

    private AdminUserRecord requireEnabledAdminUser(String phone) {
        List<AdminUserRecord> rows = jdbcTemplate.query(
            """
                SELECT id, phone, COALESCE(display_name, username, '商家后台') AS display_name, role, status, password_hash
                FROM users
                WHERE phone = ?
                """,
            (rs, rowNum) -> new AdminUserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("display_name"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("password_hash")
            ),
            phone == null ? "" : phone.trim()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }
        AdminUserRecord user = rows.get(0);
        validateAdminUser(user);
        return user;
    }

    private AdminUserRecord requireEnabledAdminUserById(long userId) {
        List<AdminUserRecord> rows = jdbcTemplate.query(
            """
                SELECT id, phone, COALESCE(display_name, username, '商家后台') AS display_name, role, status, password_hash
                FROM users
                WHERE id = ?
                """,
            (rs, rowNum) -> new AdminUserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("display_name"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("password_hash")
            ),
            userId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
        AdminUserRecord user = rows.get(0);
        validateAdminUser(user);
        return user;
    }

    private void validateAdminUser(AdminUserRecord user) {
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已停用");
        }
        String role = user.role() == null ? "" : user.role().trim().toUpperCase();
        if (!role.equals("OWNER") && !role.equals("ADMIN") && !role.equals("OPERATOR")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号无后台权限");
        }
    }

    private long extractUserId(Map<String, Object> claims) {
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
    }

    record AdminUserRecord(long id, String phone, String displayName, String role, String status, String passwordHash) {
    }
}
