package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.common.util.PasswordUtils;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.mobile.api.MobileAuthStateResponse;
import com.jzqs.app.mobile.api.MobileTokenVerifyResponse;
import com.jzqs.app.mobile.api.RiderAuthProfileResponse;
import com.jzqs.app.mobile.api.RiderAuthStateResponse;
import com.jzqs.app.mobile.api.RiderLoginResponse;
import com.jzqs.app.mobile.api.RiderTokenVerifyResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public MobileTokenVerifyResponse verify(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
        try {
            JwtClaims claims = JwtUtils.parseToken(token.trim());
            Long userId = extractUserId(claims);
            String userType = stringClaim(claims.userType());
            if (userId == null || !"customer".equalsIgnoreCase(userType)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
            }
            verifyCustomerExists(userId);

            return new MobileTokenVerifyResponse(true, userId, "customer");
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
    public MobileAuthStateResponse wxLogin(String code) {
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
    public MobileAuthStateResponse phoneLogin(String openid, String phone) {
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
    public MobileAuthStateResponse bindDevPhone(String openid, String phone) {
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
    public RiderAuthStateResponse riderWxLogin(String code) {
        String openid = buildRiderOpenid(code);
        Long riderId = findRiderIdByOpenid(openid);
        
        if (riderId == null) {
            // 未绑定手机号
            return riderAuthState(openid, false, true, null, null);
        }
        
        RiderAuthProfileResponse profile = riderProfile(riderId);
        
        if (profile.phone() == null || profile.phone().isBlank()) {
            // 有 openid 但没有手机号（不应该出现）
            return riderAuthState(openid, false, true, profile, null);
        }
        
        // 已绑定手机号，生成 token
        String token = JwtUtils.generateToken(JwtClaims.rider(riderId, profile.riderName(), profile.phone(), openid));
        
        // 更新最后登录时间
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            "UPDATE rider_profiles SET last_login_at = ? WHERE id = ?",
            Timestamp.valueOf(now),
            riderId
        );
        
        return riderAuthState(openid, true, false, profile, token);
    }

    @Override
    @Transactional
    public RiderAuthStateResponse bindRiderPhone(String openid, String phone, String nickname) {
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
        String token = JwtUtils.generateToken(JwtClaims.rider(riderId, profile.riderName(), finalPhone, finalOpenid));
        
        return riderAuthState(finalOpenid, true, false, profile, token);
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
    public RiderTokenVerifyResponse verifyRiderToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Token 不能为空");
        }
        
        try {
            // 解析 JWT token
            JwtClaims claims = JwtUtils.parseToken(token.trim());
            Long riderId = claims.riderId();
            if (riderId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Token 无效或已过期");
            }
            
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
            return new RiderTokenVerifyResponse(
                openid != null ? openid : "",
                profile.riderId(),
                profile.riderName(),
                profile.displayName(),
                profile.phone(),
                profile.areaCode(),
                profile.riderStatus(),
                "ACTIVE".equals(profile.riderStatus()),
                profile.firstLoginAt(),
                DATE_TIME_FORMATTER.format(now)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Token 无效或已过期");
        }
    }

    @Override
    @Transactional
    public MobileAuthStateResponse completeProfile(String openid, String nickname) {
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
    public MobileAuthStateResponse bindPhoneByCode(String openid, String code) {
        String finalCode = requirePhoneCode(code);
        String finalOpenid = stringClaim(openid);
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
    public MobileAuthStateResponse bindPhone(String openid, String phone, String nickname) {
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

    private Long extractUserId(JwtClaims claims) {
        return claims.effectiveUserId();
    }

    private String stringClaim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record RiderLoginCandidate(
        Long riderId,
        String riderName,
        String phone,
        String authStatus,
        String displayName,
        String currentOpenid
    ) {
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

    private MobileAuthStateResponse authState(String openid, String sessionKey, boolean registered, boolean needPhoneAuth, boolean needName, Long customerId) {
        String token = null;
        if (registered && customerId != null) {
            token = JwtUtils.generateToken(JwtClaims.customer(customerId));
        }
        return new MobileAuthStateResponse(
            (openid != null && openid.startsWith("dev_")) ? AUTH_MODE_DEV : AUTH_MODE_WECHAT,
            openid,
            sessionKey,
            registered,
            needPhoneAuth,
            needName,
            token,
            registered ? customerId : null
        );
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

    private RiderAuthStateResponse riderAuthState(
        String openid,
        boolean registered,
        boolean needPhoneAuth,
        RiderAuthProfileResponse profile,
        String token
    ) {
        if (profile == null) {
            return new RiderAuthStateResponse(
                (openid != null && (openid.startsWith("dev_") || openid.startsWith("rider_dev_") || openid.startsWith("rider_"))) ? AUTH_MODE_DEV : AUTH_MODE_WECHAT,
                openid,
                registered,
                needPhoneAuth,
                null,
                null,
                null,
                null,
                null,
                "UNAUTHORIZED",
                false,
                null,
                null,
                token
            );
        }
        return new RiderAuthStateResponse(
            (openid != null && (openid.startsWith("dev_") || openid.startsWith("rider_dev_") || openid.startsWith("rider_"))) ? AUTH_MODE_DEV : AUTH_MODE_WECHAT,
            openid,
            registered,
            needPhoneAuth,
            profile.riderId(),
            profile.riderName(),
            profile.displayName(),
            profile.phone(),
            profile.areaCode(),
            profile.riderStatus(),
            profile.workbenchEnabled(),
            profile.firstLoginAt(),
            profile.lastLoginAt(),
            token
        );
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

    @Override
    public RiderAuthProfileResponse riderProfile(long riderId) {
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
        String token = JwtUtils.generateToken(
            JwtClaims.rider(newRiderId, finalName, finalPhone, finalOpenid.isBlank() ? null : finalOpenid)
        );

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
        String phone = weChatService.getPhoneNumber(finalCode);
        String finalOpenid = stringClaim(openid);
        return riderPhoneLogin(phone, finalOpenid.isEmpty() ? null : finalOpenid);
    }

    @Override
    @Transactional
    public RiderLoginResponse riderPhoneLogin(String phone, String openid) {
        String finalPhone = requirePhone(phone);
        String finalOpenid = openid != null && !openid.trim().isEmpty() ? openid.trim() : null;
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // 查询骑手
        RiderLoginCandidate rider = jdbcTemplate.query(
            """
                SELECT id, rider_name, phone, auth_status, display_name, current_openid
                FROM rider_profiles
                WHERE phone = ?
                """,
            ps -> ps.setString(1, finalPhone),
            rs -> {
                if (!rs.next()) return null;
                return new RiderLoginCandidate(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("phone"),
                    rs.getString("auth_status"),
                    rs.getString("display_name"),
                    rs.getString("current_openid")
                );
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

        Long riderId = rider.riderId();
        String riderName = rider.riderName();
        String authStatus = rider.authStatus();

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
        String token = JwtUtils.generateToken(
            JwtClaims.rider(riderId, riderName, finalPhone, finalOpenid.isBlank() ? null : finalOpenid)
        );

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
    @Deprecated
    @Transactional
    public RiderLoginResponse riderMixedLogin(String phone, String name, String openid) {
        String finalPhone = requirePhone(phone);
        String finalName = requireNickname(name);
        String finalOpenid = openid != null && !openid.trim().isEmpty() ? openid.trim() : null;
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // 查询是否存在该手机号的骑手
        RiderLoginCandidate existingRider = jdbcTemplate.query(
            """
                SELECT id, rider_name, phone, auth_status, display_name, current_openid
                FROM rider_profiles
                WHERE phone = ?
                """,
            ps -> ps.setString(1, finalPhone),
            rs -> {
                if (!rs.next()) return null;
                return new RiderLoginCandidate(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("phone"),
                    rs.getString("auth_status"),
                    rs.getString("display_name"),
                    rs.getString("current_openid")
                );
            }
        );

        if (existingRider != null) {
            // 已有账号：验证姓名是否匹配
            String existingName = existingRider.riderName();
            String displayName = existingRider.displayName();
            
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
            Long riderId = existingRider.riderId();
            String authStatus = existingRider.authStatus();
            
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
            String token = JwtUtils.generateToken(
                JwtClaims.rider(riderId, existingName, finalPhone, finalOpenid.isBlank() ? null : finalOpenid)
            );

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
            String token = JwtUtils.generateToken(
                JwtClaims.rider(newRiderId, finalName, finalPhone, finalOpenid.isBlank() ? null : finalOpenid)
            );

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
