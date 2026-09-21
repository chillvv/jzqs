package com.jzqs.app.common.rider;

import com.jzqs.app.common.realtime.RealtimeEvent;
import com.jzqs.app.common.realtime.RealtimeEventPublisher;
import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 骑手登录会话（单一实现）。
 *
 * <p>骑手账号严格「一人一号」：同一时刻只允许一个微信持有该账号的登录态。
 * 由于 JWT 是无状态的，靠库里的 {@code rider_profiles.token_version} 兜底——
 * 每次「身份切换」递增它，签发 token 时把当前版本号写进 JWT，校验时比对，
 * 失配即说明账号已在别处重新登录，旧 token 作废（互踢）。
 *
 * <p>{@code MobileAuthServiceImpl}（/api/mobile/rider-auth/*）与
 * {@code AuthService}（/api/auth/* 的骑手分支）都必须走这里，避免出现第二套实现导致互踢被绕过。
 */
@Service
public class RiderSessionService {
    /** 会话被顶下线事件：旧设备收到后立刻退出登录，不等下一次请求才发现 token 失效 */
    public static final String SESSION_INVALIDATED_EVENT = "rider.auth.invalidated";

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public RiderSessionService(JdbcTemplate jdbcTemplate, RealtimeEventPublisher realtimeEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    /**
     * 绑定骑手微信身份。当本次登录所用 openid 与库里已绑定的不一致时
     * （换微信号、换设备后重新绑定、他人拿同一手机号登录），递增会话版本号踢掉旧会话。
     * wechat_open_id 必须同步写：它若停留在旧值，旧微信仍能凭它匹配到本账号（等于留了后门）。
     *
     * @return 是否发生身份切换（true 表示已踢掉旧会话）
     */
    public boolean takeOverIdentity(long riderId, String openid, LocalDateTime now) {
        String boundOpenid = jdbcTemplate.query(
            "SELECT current_openid FROM rider_profiles WHERE id = ?",
            ps -> ps.setLong(1, riderId),
            rs -> rs.next() ? rs.getString(1) : null
        );
        boolean changed = boundOpenid == null || boundOpenid.isBlank() || !boundOpenid.equals(openid);
        if (changed) {
            jdbcTemplate.update(
                """
                    UPDATE rider_profiles
                    SET current_openid = ?,
                        wechat_open_id = ?,
                        token_version = token_version + 1,
                        last_login_at = ?,
                        first_login_at = COALESCE(first_login_at, ?)
                    WHERE id = ?
                    """,
                openid,
                openid,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                riderId
            );
            publishInvalidated(riderId, openid);
            return true;
        }
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
        return false;
    }

    /**
     * 无条件递增会话版本号。用于拿不到 openid 的登录入口：既然无法判断是不是同一人，
     * 就按「新登录顶掉旧登录」处理，保证一人一号不被绕过。
     */
    public void bumpVersion(long riderId) {
        jdbcTemplate.update(
            "UPDATE rider_profiles SET token_version = token_version + 1 WHERE id = ?",
            riderId
        );
        publishInvalidated(riderId, null);
    }

    /**
     * 通知该骑手当前所有在线连接「登录态已作废」。
     * payload 带上新登录者的 openid：刚登录成功的那一端凭它认出是自己、忽略该事件，
     * 否则新登录者会在建立实时连接后把自己也踢下线。
     */
    private void publishInvalidated(long riderId, String openid) {
        if (realtimeEventPublisher == null) {
            return;
        }
        realtimeEventPublisher.publish(
            RealtimeEvent.builder(SESSION_INVALIDATED_EVENT)
                .audience("rider:id:" + riderId)
                .payload("riderId", riderId)
                .payload("openid", openid == null ? "" : openid)
                .build()
        );
    }

    /** 只刷新登录时间，不动会话版本号（本人自动登录场景，避免把正在送单的自己踢下线） */
    public void touchLoginTime(long riderId, LocalDateTime now) {
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

    /** 签发骑手 token，带上库里当前的会话版本号 */
    public String issueToken(long riderId, String riderName, String phone, String openid) {
        Long version = currentVersion(riderId);
        return JwtUtils.generateToken(
            JwtClaims.rider(riderId, riderName, phone, openid, version == null ? 1L : version)
        );
    }

    /**
     * token 携带的会话版本号是否仍是该骑手当前的有效版本。
     * 传入 null（老 token 不含版本号）返回 true：按兼容放行，等骑手下次登录自然带上版本号。
     */
    public boolean isTokenCurrent(long riderId, Long tokenVersion) {
        if (tokenVersion == null) {
            return true;
        }
        Long current = currentVersion(riderId);
        return current != null && current.longValue() == tokenVersion.longValue();
    }

    private Long currentVersion(long riderId) {
        return jdbcTemplate.query(
            "SELECT token_version FROM rider_profiles WHERE id = ?",
            ps -> ps.setLong(1, riderId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }
}
