package com.jzqs.app.mobile;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.wechat.WeChatService;
import com.jzqs.app.settings.service.SettingsService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NightlyReminderModule {

    private static final Logger log = LoggerFactory.getLogger(NightlyReminderModule.class);

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final WeChatService weChatService;
    private final SettingsService settingsService;

    NightlyReminderModule(JdbcTemplate jdbcTemplate, WeChatService weChatService, SettingsService settingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.weChatService = weChatService;
        this.settingsService = settingsService;
    }

    /**
     * 群发每晚提醒给所有已授权用户。
     * 前置条件（任一不满足则跳过整轮发送）：
     *  1. 系统设置开启「每晚提醒」总开关；
     *  2. 明天有菜单排期（即明天营业、可下单），否则不发。
     * 发送对象过滤：仅发送给仍有剩余餐数且钱包未过期的用户。
     * 返回成功发送条数。
     */
    public int sendNightlyReminders() {
        if (!settingsService.operationSettings().nightlyReminderEnabled()) {
            log.info("每晚提醒未开启，跳过发送");
            return 0;
        }
        LocalDate tomorrow = LocalDate.now(APP_ZONE).plusDays(1);
        boolean tomorrowHasMenu = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) > 0 FROM menu_week_items WHERE serve_date = ?",
            Boolean.class,
            tomorrow
        ));
        if (!tomorrowHasMenu) {
            log.info("明天({})无菜单排期，不营业，跳过每晚提醒", tomorrow);
            return 0;
        }

        // 取所有已授权、有剩余餐数且钱包未过期的用户
        List<NightlySendTarget> targets = jdbcTemplate.query(
            """
            SELECT
                c.id AS customer_id,
                COALESCE(c.current_openid, c.openid, '') AS current_openid,
                COALESCE(wallet_summary.remaining_meals, 0) AS remaining_meals
            FROM customer_nightly_subscriptions cds
            JOIN customers c ON c.id = cds.customer_id
            LEFT JOIN (
                SELECT customer_id,
                       COALESCE(SUM(total_meals), 0)
                           - COALESCE(SUM(reserved_meals), 0)
                           - COALESCE(SUM(consumed_meals), 0) AS remaining_meals
                FROM meal_wallets
                WHERE active = TRUE
                  AND (expired_at IS NULL OR expired_at >= CURDATE())
                GROUP BY customer_id
                HAVING remaining_meals > 0
            ) wallet_summary ON wallet_summary.customer_id = c.id
            WHERE cds.status = 'AUTHORIZED'
              AND COALESCE(c.current_openid, c.openid, '') <> ''
              AND COALESCE(wallet_summary.remaining_meals, 0) > 0
            """,
            (rs, rowNum) -> new NightlySendTarget(
                rs.getLong("customer_id"),
                rs.getString("current_openid"),
                rs.getInt("remaining_meals")
            )
        );

        int success = 0;
        String description = settingsService.operationSettings().nightlyReminderDescription();
        String tip = settingsService.operationSettings().nightlyReminderTip();
        for (NightlySendTarget target : targets) {
            try {
                weChatService.sendNightlySubscribeMessage(
                    target.openid(),
                    weChatService.buildNightlyPage(),
                    target.remainingMeals(),
                    description,
                    tip
                );
                markSent(target.customerId(), null);
                success++;
            } catch (BusinessException e) {
                log.warn("每晚提醒发送失败 customerId={}, err={}", target.customerId(), e.getMessage());
                markSent(target.customerId(), e.getMessage());
            } catch (Exception e) {
                log.warn("每晚提醒发送异常 customerId={}", target.customerId(), e);
                markSent(target.customerId(), e.getMessage());
            }
        }
        log.info("每晚提醒发送完成, count={}, tomorrow={}", success, tomorrow);
        return success;
    }

    /**
     * 单用户「每晚提醒」测试下发：使用后台运营设置的实际描述/温馨提示与钱包剩余餐数，
     * 与正式群发内容保持一致（即“后台什么样就发什么”）。
     * @return 跳转页路径
     */
    public String sendTestMessage(long customerId) {
        try {
            List<NightlySendTarget> targets = jdbcTemplate.query(
                """
                SELECT
                    c.id AS customer_id,
                    COALESCE(c.current_openid, c.openid, '') AS current_openid,
                    COALESCE(wallet_summary.remaining_meals, 0) AS remaining_meals
                FROM customers c
                LEFT JOIN (
                    SELECT customer_id,
                           COALESCE(SUM(total_meals), 0)
                               - COALESCE(SUM(reserved_meals), 0)
                               - COALESCE(SUM(consumed_meals), 0) AS remaining_meals
                    FROM meal_wallets
                    WHERE active = TRUE
                      AND (expired_at IS NULL OR expired_at >= CURDATE())
                    GROUP BY customer_id
                    HAVING remaining_meals > 0
                    ) wallet_summary ON wallet_summary.customer_id = c.id
                    WHERE c.id = ? AND c.active = TRUE
                    """,
                    (rs, rowNum) -> new NightlySendTarget(
                        rs.getLong("customer_id"),
                        rs.getString("current_openid"),
                        rs.getInt("remaining_meals")
                    ),
                    customerId
                    );
            String openid = targets.isEmpty() ? "" : targets.get(0).openid();
            if (openid == null || openid.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前账号缺少可用的微信接收标识，或暂无剩余餐数");
            }
            NightlySendTarget target = targets.get(0);
            int remainingMeals = target.remainingMeals();
            if (remainingMeals <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前账号暂无剩余餐数，无法发送每晚提醒测试（至少需要1餐）");
            }
            String description = settingsService.operationSettings().nightlyReminderDescription();
            String tip = settingsService.operationSettings().nightlyReminderTip();
            String page = weChatService.buildNightlyPage();
            weChatService.sendNightlySubscribeMessage(
                target.openid(),
                page,
                remainingMeals,
                description,
                tip
            );
            return page;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("每晚提醒测试发送异常 customerId={}", customerId, e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "每晚提醒测试发送失败：" + e.getMessage());
        }
    }

    private void markSent(long customerId, String errorMessage) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(
            """
            UPDATE customer_nightly_subscriptions
            SET last_sent_at = ?,
                last_error_message = ?,
                updated_at = ?
            WHERE customer_id = ?
            """,
            now,
            errorMessage,
            now,
            customerId
        );
    }

    /** 记录用户授权（小程序端勾选"总是保持"后回调） */
    public void authorizeNightlySubscription(long customerId, String templateId) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_nightly_subscriptions WHERE customer_id = ?",
            Integer.class,
            customerId
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                """
                UPDATE customer_nightly_subscriptions
                SET status = 'AUTHORIZED',
                    template_id = ?,
                    authorized_at = ?,
                    updated_at = ?
                WHERE customer_id = ?
                """,
                templateId,
                now,
                now,
                customerId
            );
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO customer_nightly_subscriptions
                    (customer_id, template_id, status, source, authorized_at, created_at, updated_at)
                VALUES (?, ?, 'AUTHORIZED', 'MINIAPP', ?, ?, ?)
                """,
                customerId,
                templateId,
                now,
                now,
                now
            );
        }
    }

    /** 查询用户是否已授权「每晚用餐提醒」(status = AUTHORIZED)。 */
    public boolean isSubscribed(long customerId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM customer_nightly_subscriptions WHERE customer_id = ? AND status = 'AUTHORIZED'",
            Integer.class,
            customerId
        );
        return count != null && count > 0;
    }

    /** 取消「每晚用餐提醒」订阅（置为非授权状态），后台将不再向该用户推送。 */
    public void cancelNightlySubscription(long customerId) {
        jdbcTemplate.update(
            """
            UPDATE customer_nightly_subscriptions
            SET status = 'CANCELLED',
                updated_at = ?
            WHERE customer_id = ?
            """,
            Timestamp.valueOf(LocalDateTime.now()),
            customerId
        );
    }

    private record NightlySendTarget(
        long customerId,
        String openid,
        int remainingMeals
    ) {
    }
}
