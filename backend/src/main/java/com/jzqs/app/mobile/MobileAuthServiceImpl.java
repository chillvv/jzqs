package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.common.util.PasswordUtils;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.mobile.api.RiderAuthProfileResponse;
import com.jzqs.app.mobile.api.RiderLoginResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileAuthServiceImpl implements MobileAuthService {
    private static final String AUTH_MODE_DEV = "DEV_SIMULATION";
    private static final String AUTH_MODE_WECHAT = "MINIAPP_WX";
    private static final String SOURCE_DEV = "MINIAPP_DEV";
    private static final String SOURCE_WX_PHONE = "MINIAPP_WX_PHONE";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final WeChatService weChatService;
    private final JdbcTemplate jdbcTemplate;

    public MobileAuthServiceImpl(WeChatService weChatService, JdbcTemplate jdbcTemplate) {
        this.weChatService = weChatService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> verify(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
        try {
            Map<String, Object> claims = JwtUtils.parseToken(token.trim());
            Long userId = extractUserId(claims);
            String userType = stringClaim(claims.get("userType"));
            if (userId == null || !"customer".equalsIgnoreCase(userType)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
            }
            verifyCustomerExists(userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", true);
            result.put("userId", userId);
            result.put("userType", "customer");
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
    }

    @Override
    public void logout(String token) {
        // 当前 JWT 为无状态实现，前端清除本地 token 即可
    }

    @Override
    public Map<String, Object> wxLogin(String code) {
        WeChatService.WeChatSession session = weChatService.code2Session(code);
        String openid = session.openid();
        Long customerId = findCustomerIdByOpenid(openid);
        if (customerId == null) {
            return authState(openid, session.sessionKey(), false, true, false, null);
        }
        syncCustomerWechatSession(customerId, openid, session.sessionKey());
        if (!hasBoundPhone(customerId)) {
            return authState(openid, session.sessionKey(), false, true, false, null);
        }
        return authState(openid, session.sessionKey(), true, false, false, customerId);
    }

    @Override
    @Transactional
    public Map<String, Object> phoneLogin(String openid, String phone) {
        String finalOpenid = requireOpenid(openid);
        String finalPhone = requirePhone(phone);
        LocalDateTime now = LocalDateTime.now();
        Long customerId = findCustomerIdByPhone(finalPhone);
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册，请先注册");
        }
        jdbcTemplate.update(
            """
                UPDATE customers
                SET openid = ?,
                    current_openid = ?,
                    session_key = ?,
                    profile_completed = TRUE,
                    last_login_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
            finalOpenid,
            finalOpenid,
            "session_" + finalOpenid,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            customerId
        );
        return authState(finalOpenid, "session_" + finalOpenid, true, false, false, customerId);
    }

    @Override
    @Transactional
    public Map<String, Object> bindDevPhone(String openid, String phone) {
        String finalOpenid = requireOpenid(openid);
        String finalPhone = requirePhone(phone);
        LocalDateTime now = LocalDateTime.now();
        Long customerId = findCustomerIdByOpenid(finalOpenid);
        validateUniqueCustomerPhone(finalPhone, customerId);
        if (customerId == null) {
            jdbcTemplate.update(
                """
                    INSERT INTO customers (
                        name, phone, source, source_channel, openid, current_openid, session_key,
                        profile_completed, active, customer_status, registered_at, last_login_at, openid_updated_at,
                        created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                guestNickname(finalPhone),
                finalPhone,
                SOURCE_DEV,
                SOURCE_DEV,
                finalOpenid,
                finalOpenid,
                "dev_session_" + finalOpenid,
                true,
                true,
                "FORMAL",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            customerId = findCustomerIdByPhone(finalPhone);
        } else {
            jdbcTemplate.update(
                """
                    UPDATE customers
                    SET phone = ?,
                        openid = ?,
                        current_openid = ?,
                        session_key = ?,
                        source = ?,
                        source_channel = ?,
                        profile_completed = TRUE,
                        customer_status = 'FORMAL',
                        registered_at = COALESCE(registered_at, ?),
                        last_login_at = ?,
                        openid_updated_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                finalPhone,
                finalOpenid,
                finalOpenid,
                "dev_session_" + finalOpenid,
                SOURCE_DEV,
                SOURCE_DEV,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                customerId
            );
        }
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到可绑定的客户");
        }
        ensureCustomerFormalWithZeroMealWallet(customerId, now);
        return authState(finalOpenid, "dev_session_" + finalOpenid, true, false, false, customerId);
    }

    @Override
    public Map<String, Object> riderWxLogin(String code) {
        String openid = buildRiderOpenid(code);
        Long riderId = findRiderIdByOpenid(openid);
        
        if (riderId == null) {
            // 未绑定手机号
            return riderAuthState(openid, false, true, null);
        }
        
        RiderAuthProfileResponse profile = riderProfile(riderId);
        
        if (profile.phone() == null || profile.phone().isBlank()) {
            // 有 openid 但没有手机号（不应该出现）
            return riderAuthState(openid, false, true, profile);
        }
        
        // 已绑定手机号，生成 token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("riderId", riderId);
        claims.put("riderName", profile.riderName());
        claims.put("phone", profile.phone());
        claims.put("openid", openid);
        String token = JwtUtils.generateToken(claims);
        
        // 更新最后登录时间
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            "UPDATE rider_profiles SET last_login_at = ? WHERE id = ?",
            Timestamp.valueOf(now),
            riderId
        );
        
        Map<String, Object> authState = riderAuthState(openid, true, false, profile);
        authState.put("token", token);  // 添加 token
        return authState;
    }

    @Override
    @Transactional
    public Map<String, Object> bindRiderPhone(String openid, String phone, String nickname) {
        String finalOpenid = requireOpenid(openid);
        String finalPhone = requirePhone(phone);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long riderId = findRiderIdByPhone(finalPhone);
        
        if (riderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "该手机号未注册骑手账号");
        }

        // 仅允许后台已建档骑手完成 openid 绑定
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET current_openid = ?,
                    wechat_open_id = COALESCE(wechat_open_id, ?),
                    last_login_at = ?,
                    first_login_at = COALESCE(first_login_at, ?)
                WHERE id = ?
                """,
            finalOpenid,
            finalOpenid,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            riderId
        );
        
        RiderAuthProfileResponse profile = riderProfile(riderId);
        
        // 生成 token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("riderId", riderId);
        claims.put("riderName", profile.riderName());
        claims.put("phone", finalPhone);
        claims.put("openid", finalOpenid);
        String token = JwtUtils.generateToken(claims);
        
        Map<String, Object> authState = riderAuthState(finalOpenid, true, false, profile);
        authState.put("token", token);  // 添加 token
        return authState;
    }

    @Override
    public RiderAuthProfileResponse riderProfile(String riderName) {
        String finalRiderName = requireNickname(riderName);
        Long riderId = jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE rider_name = ?",
            ps -> ps.setString(1, finalRiderName),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (riderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到骑手档案");
        }
        return riderProfile(riderId);
    }

    @Override
    public Map<String, Object> verifyRiderToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Token 不能为空");
        }
        
        try {
            // 解析 JWT token
            Map<String, Object> claims = JwtUtils.parseToken(token.trim());
            Long riderId = ((Number) claims.get("riderId")).longValue();
            
            // 查询骑手信息
            RiderAuthProfileResponse profile = riderProfile(riderId);
            
            // 检查骑手状态
            if (profile.riderStatus() == null || profile.riderStatus().equals("NOT_FOUND")) {
                throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "骑手不存在");
            }
            
            // 更新最后登录时间
            LocalDateTime now = LocalDateTime.now().withNano(0);
            jdbcTemplate.update(
                "UPDATE rider_profiles SET last_login_at = ? WHERE id = ?",
                Timestamp.valueOf(now),
                riderId
            );
            
            // 查询 openid
            String openid = jdbcTemplate.query(
                "SELECT current_openid FROM rider_profiles WHERE id = ?",
                ps -> ps.setLong(1, riderId),
                rs -> rs.next() ? rs.getString(1) : ""
            );
            
            // 返回认证状态
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("openid", openid != null ? openid : "");
            result.put("riderId", profile.riderId());
            result.put("name", profile.riderName());
            result.put("displayName", profile.displayName());
            result.put("phone", profile.phone());
            result.put("areaCode", profile.areaCode());
            result.put("status", profile.riderStatus());
            result.put("workbenchEnabled", "ACTIVE".equals(profile.riderStatus()));
            result.put("firstLoginAt", profile.firstLoginAt());
            result.put("lastLoginAt", DATE_TIME_FORMATTER.format(now));
            
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Token 无效或已过期");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> completeProfile(String openid, String nickname) {
        String finalOpenid = requireOpenid(openid);
        String finalNickname = requireNickname(nickname);
        Long customerId = findCustomerIdByOpenid(finalOpenid);
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "登录态已失效，请重新进入小程序");
        }
        validateUniqueNickname(finalNickname, customerId);
        jdbcTemplate.update(
            """
                UPDATE customers
                SET name = ?, profile_completed = TRUE, updated_at = ?
                WHERE id = ?
                """,
            finalNickname,
            Timestamp.valueOf(LocalDateTime.now()),
            customerId
        );
        return authState(finalOpenid, null, true, false, false, customerId);
    }

    @Override
    @Transactional
    public Map<String, Object> bindPhoneByCode(String openid, String code) {
        String finalCode = requirePhoneCode(code);
        String finalOpenid = stringClaim(openid);
        // #region debug-point B:customer-bind-by-code-entry
        debugReport("B", "MobileAuthServiceImpl.java:bindPhoneByCode:entry", "[DEBUG] customer bindPhoneByCode entry", Map.of("hasOpenid", !finalOpenid.isEmpty(), "codeLength", finalCode.length()));
        // #endregion
        String finalPhone = weChatService.getPhoneNumber(finalCode);
        LocalDateTime now = LocalDateTime.now();
        Long customerId = finalOpenid.isEmpty() ? null : findCustomerIdByOpenid(finalOpenid);
        if (customerId == null) {
            customerId = findCustomerIdByPhone(finalPhone);
        }

        if (customerId == null) {
            jdbcTemplate.update(
                """
                    INSERT INTO customers (
                        name, phone, source, source_channel, openid, current_openid, session_key,
                        profile_completed, active, customer_status, registered_at, last_login_at, openid_updated_at,
                        created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                guestNickname(finalPhone),
                finalPhone,
                "MINIAPP",
                SOURCE_WX_PHONE,
                finalOpenid.isEmpty() ? null : finalOpenid,
                finalOpenid.isEmpty() ? null : finalOpenid,
                finalOpenid.isEmpty() ? null : "session_" + finalOpenid,
                true,
                true,
                "FORMAL",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            customerId = findCustomerIdByPhone(finalPhone);
        } else {
            jdbcTemplate.update(
                """
                    UPDATE customers
                    SET phone = ?,
                        openid = CASE WHEN ? = '' THEN openid ELSE ? END,
                        current_openid = CASE WHEN ? = '' THEN current_openid ELSE ? END,
                        openid_updated_at = CASE WHEN ? = '' THEN openid_updated_at ELSE ? END,
                        session_key = CASE WHEN ? = '' THEN session_key ELSE ? END,
                        source = ?,
                        source_channel = ?,
                        profile_completed = TRUE,
                        customer_status = 'FORMAL',
                        registered_at = COALESCE(registered_at, ?),
                        last_login_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                finalPhone,
                finalOpenid,
                finalOpenid,
                finalOpenid,
                finalOpenid,
                finalOpenid,
                Timestamp.valueOf(now),
                finalOpenid,
                "session_" + finalOpenid,
                "MINIAPP",
                SOURCE_WX_PHONE,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                customerId
            );
        }

        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "微信手机号登录失败");
        }
        ensureCustomerFormalWithZeroMealWallet(customerId, now);

        return authState(finalOpenid, finalOpenid.isEmpty() ? null : "session_" + finalOpenid, true, false, false, customerId);
    }

    @Override
    @Transactional
    public Map<String, Object> bindPhone(String openid, String phone, String nickname) {
        String finalOpenid = requireOpenid(openid);
        String finalPhone = requirePhone(phone);
        String finalNickname = requireNickname(nickname);
        LocalDateTime now = LocalDateTime.now();
        Long customerId = findCustomerIdByOpenid(finalOpenid);
        validateUniqueCustomerPhone(finalPhone, customerId);
        if (customerId == null) {
            validateUniqueNickname(finalNickname, null);
            jdbcTemplate.update(
                """
                    INSERT INTO customers (
                        name, phone, source, source_channel, openid, current_openid, session_key,
                        profile_completed, active, customer_status, registered_at, last_login_at, openid_updated_at,
                        created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                finalNickname,
                finalPhone,
                "MINIAPP",
                SOURCE_WX_PHONE,
                finalOpenid,
                finalOpenid,
                "session_" + finalOpenid,
                true,
                true,
                "FORMAL",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            customerId = findCustomerIdByPhone(finalPhone);
        } else {
            validateUniqueNickname(finalNickname, customerId);
            jdbcTemplate.update(
                """
                    UPDATE customers
                    SET name = ?,
                        openid = ?,
                        current_openid = ?,
                        openid_updated_at = ?,
                        session_key = ?,
                        source = ?,
                        source_channel = ?,
                        profile_completed = TRUE,
                        customer_status = 'FORMAL',
                        registered_at = COALESCE(registered_at, ?),
                        last_login_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                finalNickname,
                finalOpenid,
                finalOpenid,
                Timestamp.valueOf(now),
                "session_" + finalOpenid,
                "MINIAPP",
                SOURCE_WX_PHONE,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                customerId
            );
        }
        ensureCustomerFormalWithZeroMealWallet(customerId, now);

        return authState(finalOpenid, "session_" + finalOpenid, true, false, false, customerId);
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

    private Long extractUserId(Map<String, Object> claims) {
        Object userId = claims.get("userId");
        if (userId == null) {
            userId = claims.get("customerId");
        }
        if (userId instanceof Long value) {
            return value;
        }
        if (userId instanceof Number value) {
            return value.longValue();
        }
        return null;
    }

    private String stringClaim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private void ensureCustomerFormalWithZeroMealWallet(Long customerId, LocalDateTime now) {
        if (customerId == null) {
            return;
        }
        jdbcTemplate.update(
            """
                UPDATE customers
                SET customer_status = 'FORMAL',
                    registered_at = COALESCE(registered_at, ?),
                    updated_at = ?
                WHERE id = ?
                """,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            customerId
        );
        Integer walletCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            Integer.class,
            customerId
        );
        if (walletCount == null || walletCount == 0) {
            jdbcTemplate.update(
                """
                    INSERT INTO meal_wallets (
                        customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, last_adjusted_at
                    ) VALUES (?, 0, 0, 0, TRUE, ?, ?)
                    """,
                customerId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
        }
    }

    private boolean hasBoundPhone(Long customerId) {
        String phone = jdbcTemplate.query(
            "SELECT phone FROM customers WHERE id = ?",
            ps -> ps.setLong(1, customerId),
            rs -> rs.next() ? rs.getString(1) : null
        );
        return phone != null && !phone.trim().isEmpty();
    }

    private void validateUniqueNickname(String nickname, Long currentCustomerId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM customers
                WHERE name = ?
                  AND active = TRUE
                  AND (? IS NULL OR id <> ?)
                """,
            Integer.class,
            nickname,
            currentCustomerId,
            currentCustomerId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "姓名已存在，请更换姓名");
        }
    }

    private void validateUniqueCustomerPhone(String phone, Long currentCustomerId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM customers
                WHERE phone = ?
                  AND active = TRUE
                  AND (? IS NULL OR id <> ?)
                """,
            Integer.class,
            phone,
            currentCustomerId,
            currentCustomerId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号已存在，请更换手机号");
        }
    }

    private void validateUniqueRiderName(String riderName, Long currentRiderId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM rider_profiles
                WHERE rider_name = ?
                  AND (? IS NULL OR id <> ?)
                """,
            Integer.class,
            riderName,
            currentRiderId,
            currentRiderId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "骑手姓名已存在");
        }
    }

    private Map<String, Object> authState(String openid, String sessionKey, boolean registered, boolean needPhoneAuth, boolean needName, Long customerId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("authMode", (openid != null && openid.startsWith("dev_")) ? AUTH_MODE_DEV : AUTH_MODE_WECHAT);
        state.put("openid", openid);
        state.put("sessionKey", sessionKey);
        state.put("registered", registered);
        state.put("needPhoneAuth", needPhoneAuth);
        state.put("needName", needName);
        if (registered && customerId != null) {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("userId", customerId);
            claims.put("customerId", customerId);
            claims.put("userType", "customer");
            state.put("token", JwtUtils.generateToken(claims));
            state.put("customerId", customerId);
        }
        return state;
    }

    private void syncCustomerWechatSession(Long customerId, String openid, String sessionKey) {
        if (customerId == null || openid == null || openid.isBlank()) {
            return;
        }
        jdbcTemplate.update(
            """
                UPDATE customers
                SET openid = ?,
                    current_openid = ?,
                    session_key = ?,
                    openid_updated_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
            openid,
            openid,
            sessionKey,
            Timestamp.valueOf(LocalDateTime.now()),
            Timestamp.valueOf(LocalDateTime.now()),
            customerId
        );
    }

    private Map<String, Object> riderAuthState(
        String openid,
        boolean registered,
        boolean needPhoneAuth,
        RiderAuthProfileResponse profile
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("authMode", (openid != null && (openid.startsWith("dev_") || openid.startsWith("rider_dev_") || openid.startsWith("rider_"))) ? AUTH_MODE_DEV : AUTH_MODE_WECHAT);
        state.put("openid", openid);
        state.put("registered", registered);
        state.put("needPhoneAuth", needPhoneAuth);
        if (profile == null) {
            state.put("riderStatus", "UNAUTHORIZED");
            state.put("workbenchEnabled", false);
            return state;
        }
        state.put("riderId", profile.riderId());
        state.put("riderName", profile.riderName());
        state.put("displayName", profile.displayName());
        state.put("phone", profile.phone());
        state.put("areaCode", profile.areaCode());
        state.put("riderStatus", profile.riderStatus());
        state.put("workbenchEnabled", profile.workbenchEnabled());
        state.put("firstLoginAt", profile.firstLoginAt());
        state.put("lastLoginAt", profile.lastLoginAt());
        return state;
    }

    private String buildDevOpenid(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信登录凭证不能为空");
        }
        return "dev_" + normalized;
    }

    private String buildRiderOpenid(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信登录凭证不能为空");
        }
        return "rider_" + normalized;
    }

    private String requireOpenid(String openid) {
        String value = openid == null ? "" : openid.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少微信身份标识");
        }
        return value;
    }

    private String requirePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入正确的11位手机号");
        }
        return value;
    }

    private String requirePhoneCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信手机号动态令牌不能为空");
        }
        return value;
    }

    private String requireNickname(String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入姓名");
        }
        if (!value.matches("^[\\u4e00-\\u9fa5A-Za-z·\\s]{2,20}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入正确的姓名");
        }
        return value;
    }

    private String guestNickname(String phone) {
        return "微信用户-" + phone.substring(phone.length() - 4);
    }

    private RiderAuthProfileResponse riderNotFoundProfile(String phone) {
        return new RiderAuthProfileResponse(
            0L,
            "",
            "未开通骑手",
            phone,
            "",
            "NOT_FOUND",
            false,
            "",
            ""
        );
    }

    private RiderAuthProfileResponse riderProfile(long riderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    rider_name,
                    COALESCE(display_name, rider_name) AS display_name,
                    phone,
                    default_area_code,
                    auth_status,
                    first_login_at,
                    last_login_at
                FROM rider_profiles
                WHERE id = ?
                """,
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到骑手档案");
                }
                String riderStatus = rs.getString("auth_status");
                return new RiderAuthProfileResponse(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("display_name"),
                    rs.getString("phone"),
                    rs.getString("default_area_code"),
                    riderStatus,
                    "ACTIVE".equalsIgnoreCase(riderStatus),
                    formatTimestamp(rs.getTimestamp("first_login_at")),
                    formatTimestamp(rs.getTimestamp("last_login_at"))
                );
            }
        );
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    @Override
    @Transactional
    public RiderLoginResponse riderRegister(String phone, String name, String openid) {
        String finalPhone = requirePhone(phone);
        String finalName = requireNickname(name);
        String finalOpenid = openid != null && !openid.trim().isEmpty() ? openid.trim() : null;
        LocalDateTime now = LocalDateTime.now().withNano(0);
        validateUniqueRiderName(finalName, null);

        // 检查手机号是否已注册
        Long existingRiderId = jdbcTemplate.query(
            "SELECT id FROM rider_profiles WHERE phone = ?",
            ps -> ps.setString(1, finalPhone),
            rs -> rs.next() ? rs.getLong(1) : null
        );

        if (existingRiderId != null) {
            return new RiderLoginResponse(
                false,
                null,
                0L,
                finalName,
                finalPhone,
                "UNAUTHORIZED",
                "该手机号已注册，请直接登录"
            );
        }

        // 创建新骑手账号
        Long newRiderId = jdbcTemplate.query(
            """
                INSERT INTO rider_profiles (
                    rider_name, phone, display_name, current_openid, auth_status,
                    first_login_at, last_login_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            ps -> {
                ps.setString(1, finalName);
                ps.setString(2, finalPhone);
                ps.setString(3, finalName);
                ps.setString(4, finalOpenid);
                ps.setString(5, "PENDING");
                ps.setTimestamp(6, Timestamp.valueOf(now));
                ps.setTimestamp(7, Timestamp.valueOf(now));
                ps.setTimestamp(8, Timestamp.valueOf(now));
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );

        if (newRiderId == null) {
            newRiderId = jdbcTemplate.query(
                "SELECT id FROM rider_profiles WHERE phone = ?",
                ps -> ps.setString(1, finalPhone),
                rs -> rs.next() ? rs.getLong(1) : null
            );
        }

        if (newRiderId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "创建骑手账号失败");
        }

        // 生成 JWT Token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("riderId", newRiderId);
        claims.put("riderName", finalName);
        claims.put("phone", finalPhone);
        if (finalOpenid != null) {
            claims.put("openid", finalOpenid);
        }
        String token = JwtUtils.generateToken(claims);

        return new RiderLoginResponse(
            true,
            token,
            newRiderId,
            finalName,
            finalPhone,
            "PENDING",
            "注册成功，待管理员审核通过后即可接单"
        );
    }

    @Override
    @Transactional
    public RiderLoginResponse riderWechatLogin(String openid, String code) {
        String finalCode = requirePhoneCode(code);
        // #region debug-point B:rider-wechat-login-entry
        debugReport("B", "MobileAuthServiceImpl.java:riderWechatLogin:entry", "[DEBUG] rider wechat login entry", Map.of("hasOpenid", openid != null && !openid.trim().isEmpty(), "codeLength", finalCode.length()));
        // #endregion
        String phone = weChatService.getPhoneNumber(finalCode);
        String finalOpenid = stringClaim(openid);
        return riderPhoneLogin(phone, finalOpenid.isEmpty() ? null : finalOpenid);
    }

    // #region debug-point B:mobile-auth-debug-report-helper
    private void debugReport(String hypothesisId, String location, String msg, Map<String, ?> data) {
        try {
            restTemplate().postForObject("http://127.0.0.1:7777/event", Map.of(
                "sessionId", "wechat-phone-login",
                "runId", "pre-fix",
                "hypothesisId", hypothesisId,
                "location", location,
                "msg", msg,
                "data", data,
                "ts", System.currentTimeMillis()
            ), String.class);
        } catch (Exception ignored) {
        }
    }

    private org.springframework.web.client.RestTemplate restTemplate() {
        return new org.springframework.web.client.RestTemplate();
    }
    // #endregion

    @Override
    @Transactional
    public RiderLoginResponse riderPhoneLogin(String phone, String openid) {
        String finalPhone = requirePhone(phone);
        String finalOpenid = openid != null && !openid.trim().isEmpty() ? openid.trim() : null;
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // 查询骑手
        Map<String, Object> rider = jdbcTemplate.query(
            """
                SELECT id, rider_name, phone, auth_status, display_name, current_openid
                FROM rider_profiles
                WHERE phone = ?
                """,
            ps -> ps.setString(1, finalPhone),
            rs -> {
                if (!rs.next()) return null;
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("rider_name", rs.getString("rider_name"));
                map.put("phone", rs.getString("phone"));
                map.put("auth_status", rs.getString("auth_status"));
                map.put("display_name", rs.getString("display_name"));
                map.put("current_openid", rs.getString("current_openid"));
                return map;
            }
        );

        if (rider == null) {
            return new RiderLoginResponse(
                false,
                null,
                0L,
                "",
                finalPhone,
                "UNAUTHORIZED",
                "该手机号未注册，请先注册"
            );
        }

        Long riderId = (Long) rider.get("id");
        String riderName = (String) rider.get("rider_name");
        String authStatus = (String) rider.get("auth_status");

        // 更新登录时间和 openid
        if (finalOpenid != null) {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET current_openid = ?,
                        last_login_at = ?,
                        first_login_at = COALESCE(first_login_at, ?)
                    WHERE id = ?
                    """,
                finalOpenid,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                riderId
            );
        } else {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET last_login_at = ?,
                        first_login_at = COALESCE(first_login_at, ?)
                    WHERE id = ?
                    """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                riderId
            );
        }

        // 生成 JWT Token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("riderId", riderId);
        claims.put("riderName", riderName);
        claims.put("phone", finalPhone);
        if (finalOpenid != null) {
            claims.put("openid", finalOpenid);
        }
        String token = JwtUtils.generateToken(claims);

        return new RiderLoginResponse(
            true,
            token,
            riderId,
            riderName,
            finalPhone,
            authStatus,
            "ACTIVE".equalsIgnoreCase(authStatus) ? "登录成功" : "账号待审核，请联系管理员"
        );
    }

    @Override
    @Transactional
    public RiderLoginResponse riderMixedLogin(String phone, String name, String openid) {
        String finalPhone = requirePhone(phone);
        String finalName = requireNickname(name);
        String finalOpenid = openid != null && !openid.trim().isEmpty() ? openid.trim() : null;
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // 查询是否存在该手机号的骑手
        Map<String, Object> existingRider = jdbcTemplate.query(
            """
                SELECT id, rider_name, phone, auth_status, display_name, current_openid
                FROM rider_profiles
                WHERE phone = ?
                """,
            ps -> ps.setString(1, finalPhone),
            rs -> {
                if (!rs.next()) return null;
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("rider_name", rs.getString("rider_name"));
                map.put("phone", rs.getString("phone"));
                map.put("auth_status", rs.getString("auth_status"));
                map.put("display_name", rs.getString("display_name"));
                map.put("current_openid", rs.getString("current_openid"));
                return map;
            }
        );

        if (existingRider != null) {
            // 已有账号：验证姓名是否匹配
            String existingName = (String) existingRider.get("rider_name");
            String displayName = (String) existingRider.get("display_name");
            
            if (!finalName.equals(existingName) && !finalName.equals(displayName)) {
                return new RiderLoginResponse(
                    false,
                    null,
                    0L,
                    finalName,
                    finalPhone,
                    "UNAUTHORIZED",
                    "姓名与手机号不匹配，请确认后重试"
                );
            }

            // 验证通过，更新登录时间和 openid
            Long riderId = (Long) existingRider.get("id");
            String authStatus = (String) existingRider.get("auth_status");
            
            // 如果提供了 openid，则绑定/更新
            if (finalOpenid != null) {
                jdbcTemplate.update(
                    """
                        UPDATE rider_profiles
                        SET current_openid = ?,
                            last_login_at = ?,
                            first_login_at = COALESCE(first_login_at, ?)
                        WHERE id = ?
                        """,
                    finalOpenid,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    riderId
                );
            } else {
                jdbcTemplate.update(
                    """
                        UPDATE rider_profiles
                        SET last_login_at = ?,
                            first_login_at = COALESCE(first_login_at, ?)
                        WHERE id = ?
                        """,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    riderId
                );
            }

            // 生成 JWT Token
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("riderId", riderId);
            claims.put("riderName", existingName);
            claims.put("phone", finalPhone);
            if (finalOpenid != null) {
                claims.put("openid", finalOpenid);
            }
            String token = JwtUtils.generateToken(claims);

            return new RiderLoginResponse(
                true,
                token,
                riderId,
                existingName,
                finalPhone,
                authStatus,
                "ACTIVE".equalsIgnoreCase(authStatus) ? "登录成功" : "账号待审核，请联系管理员"
            );
        } else {
            // 新用户：自动创建账号（状态：待审核）
            Long newRiderId = jdbcTemplate.query(
                """
                    INSERT INTO rider_profiles (
                        rider_name, phone, display_name, current_openid, auth_status,
                        first_login_at, last_login_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                ps -> {
                    ps.setString(1, finalName);
                    ps.setString(2, finalPhone);
                    ps.setString(3, finalName);
                    ps.setString(4, finalOpenid);
                    ps.setString(5, "PENDING");
                    ps.setTimestamp(6, Timestamp.valueOf(now));
                    ps.setTimestamp(7, Timestamp.valueOf(now));
                    ps.setTimestamp(8, Timestamp.valueOf(now));
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return null;
                }
            );

            if (newRiderId == null) {
                // 如果插入失败，尝试查询刚创建的记录
                newRiderId = jdbcTemplate.query(
                    "SELECT id FROM rider_profiles WHERE phone = ?",
                    ps -> ps.setString(1, finalPhone),
                    rs -> rs.next() ? rs.getLong(1) : null
                );
            }

            if (newRiderId == null) {
                throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "创建骑手账号失败");
            }

            // 生成 JWT Token
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("riderId", newRiderId);
            claims.put("riderName", finalName);
            claims.put("phone", finalPhone);
            if (finalOpenid != null) {
                claims.put("openid", finalOpenid);
            }
            String token = JwtUtils.generateToken(claims);

            return new RiderLoginResponse(
                true,
                token,
                newRiderId,
                finalName,
                finalPhone,
                "PENDING",
                "账号已创建，待管理员审核通过后即可接单"
            );
        }
    }
}
