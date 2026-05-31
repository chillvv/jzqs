package com.jzqs.app.auth;

import com.jzqs.app.auth.AuthController.*;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.common.wechat.WeChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一认证服务
 * 
 * @author Kiro AI
 * @since 2026-05-23
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final WeChatService weChatService;
    private final JdbcTemplate jdbcTemplate;

    public AuthService(WeChatService weChatService, JdbcTemplate jdbcTemplate) {
        this.weChatService = weChatService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 微信静默登录
     */
    @Transactional
    public AuthLoginResponse login(String code, String userType) {
        // 1. 调用微信接口换取 openid
        WeChatService.WeChatSession session = weChatService.code2Session(code);
        String openid = session.openid();

        // 2. 根据 userType 查询用户
        if ("customer".equalsIgnoreCase(userType)) {
            return customerLogin(openid);
        } else if ("rider".equalsIgnoreCase(userType)) {
            return riderLogin(openid);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的用户类型");
        }
    }

    /**
     * 绑定手机号
     */
    @Transactional
    public AuthBindPhoneResponse bindPhone(String code, String userType) {
        // 1. 调用微信接口获取手机号
        String phone = weChatService.getPhoneNumber(code);

        // 2. 根据 userType 绑定手机号
        if ("customer".equalsIgnoreCase(userType)) {
            return bindCustomerPhone(phone);
        } else if ("rider".equalsIgnoreCase(userType)) {
            return bindRiderPhone(phone);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的用户类型");
        }
    }

    /**
     * 顾客老用户手机号登录
     */
    @Transactional
    public AuthBindPhoneResponse loginCustomerByPhone(String phone, String openid) {
        String finalPhone = validatePhone(phone);
        String finalOpenid = openid == null ? "" : openid.trim();
        LocalDateTime now = LocalDateTime.now();
        Long customerId = findCustomerIdByPhone(finalPhone);

        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册，请先注册");
        }

        if (!finalOpenid.isEmpty()) {
            jdbcTemplate.update(
                """
                UPDATE customers
                SET openid = ?, current_openid = ?, session_key = ?, profile_completed = TRUE, last_login_at = ?, updated_at = ?
                WHERE id = ?
                """,
                finalOpenid,
                finalOpenid,
                "session_" + finalOpenid,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                customerId
            );
        } else {
            updateCustomerLoginTime(customerId);
        }

        return buildCustomerAuthResponse(customerId, finalPhone);
    }

    /**
     * 统一手机号登录
     */
    @Transactional
    public AuthBindPhoneResponse loginByPhone(String phone, String openid, String userType) {
        if ("customer".equalsIgnoreCase(userType)) {
            return loginCustomerByPhone(phone, openid);
        } else if ("rider".equalsIgnoreCase(userType)) {
            return loginRiderByPhone(phone, openid);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的用户类型");
        }
    }

    /**
     * 验证 token
     */
    public AuthVerifyResponse verify(String token) {
        try {
            Map<String, Object> claims = JwtUtils.parseToken(token);
            
            // 提取 userId 和 userType
            Long userId = extractUserId(claims);
            String userType = (String) claims.get("userType");

            if (userId == null || userType == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
            }

            // 验证用户是否存在且有效
            if ("customer".equalsIgnoreCase(userType)) {
                verifyCustomerExists(userId);
            } else if ("rider".equalsIgnoreCase(userType)) {
                verifyRiderExists(userId);
            }

            return new AuthVerifyResponse(true, userId, userType);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证 token 失败", e);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
    }

    /**
     * 退出登录
     */
    public void logout(String token) {
        // TODO: 可选实现 token 黑名单
        log.info("用户退出登录");
    }

    // ==================== 顾客相关 ====================

    private AuthLoginResponse customerLogin(String openid) {
        Long customerId = findCustomerIdByOpenid(openid);
        
        if (customerId == null) {
            // 未注册
            return new AuthLoginResponse(null, null, "customer", false, openid);
        }

        // 已注册，检查是否绑定手机号
        boolean hasPhone = hasCustomerPhone(customerId);
        if (!hasPhone) {
            // 有 openid 但没手机号
            return new AuthLoginResponse(null, customerId, "customer", false, openid);
        }

        // 生成 token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", customerId);
        claims.put("userType", "customer");
        claims.put("openid", openid);
        String token = JwtUtils.generateToken(claims);

        // 更新最后登录时间
        updateCustomerLoginTime(customerId);

        return new AuthLoginResponse(token, customerId, "customer", true, openid);
    }

    private AuthBindPhoneResponse bindCustomerPhone(String phone) {
        LocalDateTime now = LocalDateTime.now();
        
        // 查找是否已有该手机号的顾客
        Long customerId = findCustomerIdByPhone(phone);
        
        if (customerId == null) {
            // 创建新顾客
            jdbcTemplate.update(
                """
                INSERT INTO customers (name, phone, source, source_channel, profile_completed, active, customer_status, registered_at, last_login_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "微信用户-" + phone.substring(phone.length() - 4),
                phone,
                "MINIAPP",
                "MINIAPP_WX_PHONE",
                true,
                true,
                "INTENTION",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            customerId = findCustomerIdByPhone(phone);
        } else {
            // 更新登录时间
            updateCustomerLoginTime(customerId);
        }

        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "绑定手机号失败");
        }

        return buildCustomerAuthResponse(customerId, phone);
    }

    private AuthBindPhoneResponse buildCustomerAuthResponse(Long customerId, String phone) {
        // 生成 token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", customerId);
        claims.put("userType", "customer");
        String token = JwtUtils.generateToken(claims);

        return new AuthBindPhoneResponse(token, customerId, "customer", maskPhone(phone), null, null, null);
    }

    private Long findCustomerIdByOpenid(String openid) {
        return jdbcTemplate.query(
            "SELECT id FROM customers WHERE (openid = ? OR current_openid = ?) AND active = TRUE",
            ps -> {
                ps.setString(1, openid);
                ps.setString(2, openid);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private Long findCustomerIdByPhone(String phone) {
        return jdbcTemplate.query(
            "SELECT id FROM customers WHERE phone = ? AND active = TRUE",
            ps -> ps.setString(1, phone),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private boolean hasCustomerPhone(Long customerId) {
        String phone = jdbcTemplate.query(
            "SELECT phone FROM customers WHERE id = ?",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getString(1) : null
        );
        return phone != null && !phone.trim().isEmpty();
    }

    private void updateCustomerLoginTime(Long customerId) {
        jdbcTemplate.update(
            "UPDATE customers SET last_login_at = ?, updated_at = ? WHERE id = ?",
            Timestamp.valueOf(LocalDateTime.now()),
            Timestamp.valueOf(LocalDateTime.now()),
            customerId
        );
    }

    private void verifyCustomerExists(Long customerId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customers WHERE id = ? AND active = TRUE",
            Integer.class,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }
    }

    // ==================== 骑手相关 ====================

    private AuthLoginResponse riderLogin(String openid) {
        Long riderId = findRiderIdByOpenid(openid);
        
        if (riderId == null) {
            // 未注册
            return new AuthLoginResponse(null, null, "rider", false, openid);
        }

        // 已注册，检查状态
        Map<String, Object> rider = getRiderInfo(riderId);
        String authStatus = (String) rider.get("auth_status");
        String phone = (String) rider.get("phone");

        if (phone == null || phone.trim().isEmpty()) {
            // 有 openid 但没手机号（不应该出现）
            return new AuthLoginResponse(null, riderId, "rider", false, openid);
        }

        // 生成 token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", riderId);
        claims.put("riderId", riderId);
        claims.put("userType", "rider");
        claims.put("openid", openid);
        String token = JwtUtils.generateToken(claims);

        // 更新最后登录时间
        updateRiderLoginTime(riderId);

        return new AuthLoginResponse(token, riderId, "rider", true, openid);
    }

    private AuthBindPhoneResponse bindRiderPhone(String phone) {
        // 查找是否已有该手机号的骑手
        Long riderId = findRiderIdByPhone(phone);
        
        if (riderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册骑手账号");
        }

        // 更新登录时间
        updateRiderLoginTime(riderId);
        Map<String, Object> rider = getRiderInfo(riderId);
        return buildRiderAuthResponse(
            riderId,
            phone,
            (String) rider.get("rider_name"),
            mapRiderStatus((String) rider.get("auth_status"))
        );
    }

    private AuthBindPhoneResponse loginRiderByPhone(String phone, String openid) {
        String finalPhone = validatePhone(phone);
        String finalOpenid = openid == null ? "" : openid.trim();
        Long riderId = findRiderIdByPhone(finalPhone);

        if (riderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册骑手账号");
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (!finalOpenid.isEmpty()) {
            jdbcTemplate.update(
                """
                UPDATE rider_profiles
                SET current_openid = ?, last_login_at = ?, first_login_at = COALESCE(first_login_at, ?)
                WHERE id = ?
                """,
                finalOpenid,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                riderId
            );
        } else {
            updateRiderLoginTime(riderId);
        }

        Map<String, Object> rider = getRiderInfo(riderId);
        return buildRiderAuthResponse(
            riderId,
            finalPhone,
            (String) rider.get("rider_name"),
            mapRiderStatus((String) rider.get("auth_status"))
        );
    }

    private Long findRiderIdByOpenid(String openid) {
        return jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE current_openid = ? OR wechat_open_id = ?",
            ps -> {
                ps.setString(1, openid);
                ps.setString(2, openid);
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private Long findRiderIdByPhone(String phone) {
        return jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE phone = ?",
            ps -> ps.setString(1, phone),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private Map<String, Object> getRiderInfo(Long riderId) {
        return jdbcTemplate.query(
            "SELECT id, rider_name, phone, auth_status FROM rider_profiles WHERE id = ?",
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "骑手不存在");
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("rider_name", rs.getString("rider_name"));
                map.put("phone", rs.getString("phone"));
                map.put("auth_status", rs.getString("auth_status"));
                return map;
            }
        );
    }

    private AuthBindPhoneResponse buildRiderAuthResponse(Long riderId, String phone, String riderName, String riderStatus) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", riderId);
        claims.put("riderId", riderId);
        claims.put("userType", "rider");
        String token = JwtUtils.generateToken(claims);
        boolean workbenchEnabled = "ACTIVE".equals(riderStatus);
        return new AuthBindPhoneResponse(
            token,
            riderId,
            "rider",
            maskPhone(phone),
            riderName,
            riderStatus,
            workbenchEnabled
        );
    }

    private String mapRiderStatus(String authStatus) {
        if (authStatus == null || authStatus.trim().isEmpty()) {
            return "NOT_FOUND";
        }
        String normalized = authStatus.trim().toUpperCase();
        if ("ACTIVE".equals(normalized) || "DISABLED".equals(normalized) || "UNASSIGNED".equals(normalized)) {
            return normalized;
        }
        return "PENDING";
    }

    private void updateRiderLoginTime(Long riderId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            "UPDATE rider_profiles SET last_login_at = ?, first_login_at = COALESCE(first_login_at, ?) WHERE id = ?",
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            riderId
        );
    }

    private void verifyRiderExists(Long riderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rider_profiles WHERE id = ?",
            Integer.class,
            riderId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "骑手不存在");
        }
    }

    /**
     * 手机号直接注册（手动输入）
     * 用户手动输入手机号+姓名完成注册/登录
     */
    @Transactional
    public AuthBindPhoneResponse registerPhone(String phone, String nickname, String openid, String userType) {
        String finalPhone = validatePhone(phone);
        String finalNickname = validateNickname(nickname);
        String finalOpenid = openid != null ? openid.trim() : "";
        LocalDateTime now = LocalDateTime.now();

        if ("customer".equalsIgnoreCase(userType)) {
            return registerCustomerPhone(finalPhone, finalNickname, finalOpenid, now);
        } else if ("rider".equalsIgnoreCase(userType)) {
            return registerRiderPhone(finalPhone, finalNickname, finalOpenid, now);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效的用户类型");
        }
    }

    private AuthBindPhoneResponse registerCustomerPhone(String phone, String nickname, String openid, LocalDateTime now) {
        Long customerId = findCustomerIdByPhone(phone);

        if (customerId == null) {
            jdbcTemplate.update(
                """
                INSERT INTO customers (name, phone, source, source_channel, openid, current_openid, session_key, profile_completed, active, customer_status, registered_at, last_login_at, openid_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                nickname,
                phone,
                "MINIAPP",
                "MANUAL_INPUT",
                openid.isEmpty() ? null : openid,
                openid.isEmpty() ? null : openid,
                openid.isEmpty() ? null : "session_" + openid,
                true,
                true,
                "INTENTION",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            customerId = findCustomerIdByPhone(phone);
        } else {
            if (!openid.isEmpty()) {
                jdbcTemplate.update(
                    """
                    UPDATE customers SET name = ?, openid = ?, current_openid = ?, session_key = ?, profile_completed = TRUE, last_login_at = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    nickname,
                    openid,
                    openid,
                    "session_" + openid,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    customerId
                );
            } else {
                jdbcTemplate.update(
                    "UPDATE customers SET name = ?, profile_completed = TRUE, last_login_at = ?, updated_at = ? WHERE id = ?",
                    nickname,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    customerId
                );
            }
        }

        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "注册失败");
        }

        return buildCustomerAuthResponse(customerId, phone);
    }

    private AuthBindPhoneResponse registerRiderPhone(String phone, String nickname, String openid, LocalDateTime now) {
        Long riderId = findRiderIdByPhone(phone);

        if (riderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册骑手账号");
        }

        jdbcTemplate.update(
            "UPDATE rider_profiles SET last_login_at = ?, first_login_at = COALESCE(first_login_at, ?) WHERE id = ?",
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            riderId
        );

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", riderId);
        claims.put("riderId", riderId);
        claims.put("userType", "rider");
        String token = JwtUtils.generateToken(claims);

        Map<String, Object> rider = getRiderInfo(riderId);
        return buildRiderAuthResponse(
            riderId,
            phone,
            (String) rider.get("rider_name"),
            mapRiderStatus((String) rider.get("auth_status"))
        );
    }

    private String validatePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入正确的11位手机号");
        }
        return value;
    }

    private String validateNickname(String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入姓名");
        }
        return value;
    }

    // ==================== 工具方法 ====================

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

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
