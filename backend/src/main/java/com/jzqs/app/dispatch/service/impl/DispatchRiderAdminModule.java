package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchRiderActivateResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthTakeoverResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthUnbindResponse;
import com.jzqs.app.dispatch.api.DispatchRiderMonthlyStatItem;
import com.jzqs.app.dispatch.api.DispatchRiderMonthlyStatsResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import com.jzqs.app.dispatch.api.DispatchRiderStatusResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchRiderAdminModule {
    private static final Logger log = LoggerFactory.getLogger(DispatchRiderAdminModule.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    DispatchRiderAdminModule(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<PendingRiderResponse> pendingRiders() {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    COALESCE(display_name, rider_name) AS display_name,
                    phone,
                    current_openid,
                    auth_status,
                    first_login_at,
                    last_login_at
                FROM rider_profiles
                WHERE auth_status = 'UNASSIGNED'
                ORDER BY CASE WHEN last_login_at IS NULL THEN 1 ELSE 0 END, last_login_at DESC, id DESC
                """,
            (rs, rowNum) -> new PendingRiderResponse(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("phone"),
                rs.getString("current_openid"),
                rs.getString("auth_status"),
                formatTimestamp(rs.getTimestamp("first_login_at")),
                formatTimestamp(rs.getTimestamp("last_login_at"))
            )
        );
    }

    List<DispatchManagedRiderResponse> managedRiders(String authStatus, String keyword, String areaCode) {
        StringBuilder sql = new StringBuilder(
            """
                SELECT
                    rp.id,
                    rp.rider_name,
                    COALESCE(rp.display_name, rp.rider_name) AS display_name,
                    rp.phone,
                    rp.auth_status,
                    rp.employment_status,
                    rp.default_area_code,
                    rp.assigned_by,
                    rp.current_openid,
                    rp.first_login_at,
                    rp.last_login_at,
                    rp.monthly_salary,
                    COALESCE((
                        SELECT SUM(db.total_count)
                        FROM dispatch_batches db
                        WHERE db.rider_profile_id = rp.id
                          AND db.serve_date = CURRENT_DATE
                    ), 0) AS today_task_count,
                    COALESCE((
                        SELECT SUM(db.delivered_count)
                        FROM dispatch_batches db
                        WHERE db.rider_profile_id = rp.id
                          AND db.serve_date = CURRENT_DATE
                    ), 0) AS today_delivered_count
                FROM rider_profiles rp
                WHERE 1 = 1
                """
        );
        List<Object> args = new ArrayList<>();
        if (authStatus != null && !authStatus.isBlank()) {
            sql.append(" AND rp.auth_status = ?");
            args.add(authStatus);
        }
        if (areaCode != null && !areaCode.isBlank()) {
            sql.append(" AND rp.default_area_code = ?");
            args.add(areaCode);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (rp.rider_name LIKE ? OR COALESCE(rp.display_name, rp.rider_name) LIKE ? OR COALESCE(rp.phone, '') LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        sql.append(" ORDER BY CASE rp.auth_status WHEN 'ACTIVE' THEN 0 WHEN 'UNASSIGNED' THEN 1 ELSE 2 END, rp.id DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DispatchManagedRiderResponse(
            rs.getLong("id"),
            rs.getString("rider_name"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getString("auth_status"),
            rs.getString("employment_status"),
            rs.getString("default_area_code"),
            rs.getString("assigned_by"),
            formatTimestamp(rs.getTimestamp("first_login_at")),
            formatTimestamp(rs.getTimestamp("last_login_at")),
            rs.getInt("today_task_count"),
            rs.getInt("today_delivered_count"),
            rs.getString("current_openid"),
            rs.getBigDecimal("monthly_salary")
        ), args.toArray());
    }

    /**
     * 骑手月度配送人工成本统计。
     * 单量口径：dispatch_assignments.status = 'DELIVERED' 的餐段订单数（一个餐段订单 = 1 单），
     * 按 daily_orders.serve_date 落在目标月份；取消/退款的订单派单行已被删除，天然不计入。
     * 占比分母为该月全部已送达单量（含未归属骑手的孤儿单），保证各骑手占比之和不超过 100%。
     */
    DispatchRiderMonthlyStatsResponse riderMonthlyStats(String month) {
        YearMonth targetMonth = parseMonth(month);
        java.sql.Date monthStart = java.sql.Date.valueOf(targetMonth.atDay(1));
        java.sql.Date monthEnd = java.sql.Date.valueOf(targetMonth.atEndOfMonth());

        Map<String, Object> totals = jdbcTemplate.queryForMap(
            """
                SELECT
                    COUNT(*) AS total_delivered,
                    COALESCE(SUM(CASE WHEN da.rider_profile_id IS NULL THEN 1 ELSE 0 END), 0) AS unassigned_delivered
                FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE da.status = 'DELIVERED'
                  AND doo.serve_date BETWEEN ? AND ?
                """,
            monthStart,
            monthEnd
        );
        int totalDelivered = ((Number) totals.get("total_delivered")).intValue();
        int unassignedDelivered = ((Number) totals.get("unassigned_delivered")).intValue();

        List<DispatchRiderMonthlyStatItem> riders = jdbcTemplate.query(
            """
                SELECT
                    rp.id,
                    rp.rider_name,
                    rp.default_area_code,
                    rp.auth_status,
                    rp.monthly_salary,
                    COALESCE(stats.delivered_count, 0) AS delivered_count
                FROM rider_profiles rp
                LEFT JOIN (
                    SELECT da.rider_profile_id AS rider_id, COUNT(*) AS delivered_count
                    FROM dispatch_assignments da
                    JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                    JOIN daily_orders doo ON doo.id = mso.daily_order_id
                    WHERE da.status = 'DELIVERED'
                      AND da.rider_profile_id IS NOT NULL
                      AND doo.serve_date BETWEEN ? AND ?
                    GROUP BY da.rider_profile_id
                ) stats ON stats.rider_id = rp.id
                ORDER BY delivered_count DESC, rp.id ASC
                """,
            (rs, rowNum) -> {
                int deliveredCount = rs.getInt("delivered_count");
                BigDecimal monthlySalary = rs.getBigDecimal("monthly_salary");
                return new DispatchRiderMonthlyStatItem(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("default_area_code"),
                    rs.getString("auth_status"),
                    deliveredCount,
                    sharePercent(deliveredCount, totalDelivered),
                    monthlySalary,
                    costPerOrder(monthlySalary, deliveredCount)
                );
            },
            monthStart,
            monthEnd
        );

        BigDecimal totalMonthlyCost = riders.stream()
            .map(DispatchRiderMonthlyStatItem::monthlySalary)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageCostPerOrder = totalDelivered <= 0 || totalMonthlyCost.signum() <= 0
            ? null
            : totalMonthlyCost.divide(BigDecimal.valueOf(totalDelivered), 2, RoundingMode.HALF_UP);

        return new DispatchRiderMonthlyStatsResponse(
            targetMonth.toString(),
            totalDelivered,
            unassignedDelivered,
            totalMonthlyCost,
            averageCostPerOrder,
            riders
        );
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "月份格式应为 YYYY-MM");
        }
    }

    /** 单量占比（%），保留 1 位小数。 */
    private static BigDecimal sharePercent(int deliveredCount, int totalDelivered) {
        if (totalDelivered <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(deliveredCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalDelivered), 1, RoundingMode.HALF_UP);
    }

    /** 单均人工成本 = 月薪 ÷ 当月单量；月薪未设置或当月无单时为 null。 */
    private static BigDecimal costPerOrder(BigDecimal monthlySalary, int deliveredCount) {
        if (monthlySalary == null || monthlySalary.signum() <= 0 || deliveredCount <= 0) {
            return null;
        }
        return monthlySalary.divide(BigDecimal.valueOf(deliveredCount), 2, RoundingMode.HALF_UP);
    }

    DispatchRiderProfileUpsertResponse createRider(
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String employmentStatus,
        BigDecimal monthlySalary,
        String updatedBy,
        AreaBindingUpdater areaBindingUpdater
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        assertRiderNameAvailable(riderName, null);
        assertPhoneAvailable(phone, riderName, null);
        boolean active = "ACTIVE".equalsIgnoreCase(employmentStatus);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long riderId = insertAndReturnId(
            """
                INSERT INTO rider_profiles (
                    rider_name,
                    display_name,
                    phone,
                    employment_status,
                    default_area_code,
                    display_order,
                    remark,
                    monthly_salary,
                    auth_status,
                    assigned_at,
                    assigned_by,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            riderName,
            displayName,
            phone,
            employmentStatus,
            normalizedAreaCode,
            0,
            null,
            monthlySalary,
            active ? "ACTIVE" : "DISABLED",
            Timestamp.valueOf(now),
            updatedBy,
            Timestamp.valueOf(now)
        );
        if (normalizedAreaCode != null && active) {
            areaBindingUpdater.update(normalizedAreaCode, null, riderId, null, updatedBy);
        }
        log.info("创建骑手: riderId={} name={} phone={} area={} employment={} monthlySalary={} operator={}",
            riderId, riderName, phone, normalizedAreaCode, employmentStatus, monthlySalary, updatedBy);
        return new DispatchRiderProfileUpsertResponse(
            riderId,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            active ? "ACTIVE" : "DISABLED",
            monthlySalary
        );
    }

    DispatchRiderProfileUpsertResponse updateRiderProfile(
        long riderId,
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        BigDecimal monthlySalary,
        String updatedBy,
        AreaBindingUpdater areaBindingUpdater
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        assertRiderNameAvailable(riderName, riderId);
        assertPhoneAvailable(phone, riderName, riderId);
        String oldAreaCode = jdbcTemplate.query(
            "SELECT default_area_code FROM rider_profiles WHERE id = ?",
            ps -> ps.setLong(1, riderId),
            rs -> rs.next() ? rs.getString("default_area_code") : null
        );
        String oldRiderName = jdbcTemplate.query(
            "SELECT rider_name FROM rider_profiles WHERE id = ?",
            ps -> ps.setLong(1, riderId),
            rs -> rs.next() ? rs.getString("rider_name") : null
        );
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET rider_name = ?,
                    display_name = ?,
                    phone = ?,
                    default_area_code = ?,
                    monthly_salary = ?,
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            monthlySalary,
            updatedBy,
            riderId
        );
        // 骑手改名时同步「文字形式的骑手姓名」到所有关联表：
        // 1) 派单记录（骑手进度页读的就是这里的 rider_name）；
        // 2) 历史异常记录。
        // 派单记录同时按 rider_profile_id 和「旧姓名 + 空档案」双匹配，覆盖历史遗留的
        // 只存了文字姓名、没有档案 ID 的老订单，保证改名后任何查询拿到的都是最新名字。
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET rider_name = ? WHERE rider_profile_id = ? OR (rider_profile_id IS NULL AND rider_name = ?)",
            riderName, riderId, oldRiderName
        );
        jdbcTemplate.update(
            "UPDATE delivery_exceptions SET rider_name = ? WHERE rider_profile_id = ?",
            riderName, riderId
        );
        if (!java.util.Objects.equals(oldAreaCode, normalizedAreaCode)) {
            log.info("骑手改区域(双写同步): riderId={} 旧区域={} 新区域={} operator={}",
                riderId, oldAreaCode, normalizedAreaCode, updatedBy);
            // 双向同步：先把该骑手从旧区域的默认骑手中释放。
            jdbcTemplate.update(
                """
                    UPDATE dispatch_area_bindings
                    SET default_rider_profile_id = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE default_rider_profile_id = ?
                    """,
                riderId
            );
            if (normalizedAreaCode != null) {
                // 订单中心同步：把该骑手名下未完成的配送单迁移到新区域（已完成的历史单保持不变）。
                jdbcTemplate.update(
                    """
                        UPDATE dispatch_assignments
                        SET area_code = ?
                        WHERE rider_profile_id = ?
                          AND status IN ('PENDING', 'AREA_ASSIGNED', 'DISPATCHING')
                        """,
                    normalizedAreaCode,
                    riderId
                );
                areaBindingUpdater.update(normalizedAreaCode, null, riderId, null, updatedBy);
            }
        }
        String riderStatus = jdbcTemplate.queryForObject(
            "SELECT auth_status FROM rider_profiles WHERE id = ?",
            String.class,
            riderId
        );
        return new DispatchRiderProfileUpsertResponse(
            riderId,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            riderStatus,
            monthlySalary
        );
    }

    DispatchRiderAuthBindingResponse riderAuthBinding(long riderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    rider_name,
                    COALESCE(display_name, rider_name) AS display_name,
                    phone,
                    current_openid,
                    auth_status,
                    last_login_at
                FROM rider_profiles
                WHERE id = ?
                """,
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new DispatchRiderAuthBindingResponse(
                    rs.getLong("id"),
                    rs.getString("rider_name"),
                    rs.getString("display_name"),
                    rs.getString("phone"),
                    rs.getString("current_openid"),
                    rs.getString("auth_status"),
                    formatTimestamp(rs.getTimestamp("last_login_at"))
                );
            }
        );
    }

    DispatchRiderAuthTakeoverResponse takeoverRiderAuth(long riderId, long sourceRiderId, String assignedBy) {
        DispatchRiderAuthSource source = loadRiderAuthSource(sourceRiderId);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET phone = COALESCE(?, phone),
                    current_openid = ?,
                    wechat_open_id = ?,
                    display_name = COALESCE(?, display_name),
                    last_login_at = COALESCE(?, last_login_at),
                    auth_status = 'ACTIVE',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            source.phone(),
            source.currentOpenid(),
            source.wechatOpenId(),
            source.displayName(),
            toTimestamp(source.lastLoginAt()),
            assignedBy,
            riderId
        );
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET current_openid = NULL,
                    wechat_open_id = NULL,
                    auth_status = 'DISABLED',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            assignedBy,
            sourceRiderId
        );
        return new DispatchRiderAuthTakeoverResponse(
            riderId,
            sourceRiderId,
            source.currentOpenid(),
            "ACTIVE"
        );
    }

    DispatchRiderAuthUnbindResponse unbindRiderAuth(long riderId, String assignedBy) {
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET current_openid = NULL,
                    wechat_open_id = NULL,
                    auth_status = 'DISABLED',
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            assignedBy,
            riderId
        );
        return new DispatchRiderAuthUnbindResponse(
            riderId,
            "",
            "DISABLED"
        );
    }

    DispatchRiderActivateResponse activateRider(
        long riderId,
        String riderName,
        String areaCode,
        String assignedBy,
        AreaBindingUpdater areaBindingUpdater
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET rider_name = ?,
                    display_name = ?,
                    default_area_code = ?,
                    auth_status = 'ACTIVE',
                    assigned_at = ?,
                    assigned_by = ?
                WHERE id = ?
                """,
            riderName,
            riderName,
            normalizedAreaCode,
            Timestamp.valueOf(now),
            assignedBy,
            riderId
        );
        if (normalizedAreaCode != null) {
            areaBindingUpdater.update(normalizedAreaCode, null, riderId, null, assignedBy);
        }
        return new DispatchRiderActivateResponse(riderId, "ACTIVE", normalizedAreaCode);
    }

    DispatchRiderStatusResponse disableRider(long riderId, String assignedBy) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET auth_status = 'DISABLED',
                    assigned_at = COALESCE(assigned_at, ?),
                    assigned_by = COALESCE(assigned_by, ?)
                WHERE id = ?
                """,
            Timestamp.valueOf(now),
            assignedBy,
            riderId
        );
        // 禁用骑手后必须把它从区域的默认/备用骑手中释放，并清空其归属区域，
        // 避免区域还挂着已禁用的骑手（否则区域管理与骑手管理将不再一致）。
        jdbcTemplate.update(
            "UPDATE dispatch_area_bindings SET default_rider_profile_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE default_rider_profile_id = ?",
            riderId
        );
        jdbcTemplate.update(
            "UPDATE dispatch_area_bindings SET backup_rider_profile_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE backup_rider_profile_id = ?",
            riderId
        );
        jdbcTemplate.update(
            "UPDATE rider_profiles SET default_area_code = NULL WHERE id = ?",
            riderId
        );
        log.info("禁用骑手并释放区域绑定: riderId={} operator={}", riderId, assignedBy);
        return new DispatchRiderStatusResponse(
            riderId,
            "DISABLED"
        );
    }

    /**
     * 校验骑手名称全局唯一（骑手唯一：一个姓名对应一条档案）。
     */
    private void assertRiderNameAvailable(String riderName, Long excludeRiderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rider_profiles WHERE rider_name = ? AND (? IS NULL OR id <> ?)",
            Integer.class,
            riderName == null ? null : riderName.trim(),
            excludeRiderId,
            excludeRiderId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "骑手名称已存在");
        }
    }

    /**
     * 校验手机号不能绑定到「不同姓名」的骑手（手机号唯一；同一骑手跨餐期可共用同一手机号）。
     */
    private void assertPhoneAvailable(String phone, String riderName, Long excludeRiderId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rider_profiles WHERE phone = ? AND COALESCE(rider_name, '') <> ? AND (? IS NULL OR id <> ?)",
            Integer.class,
            phone.trim(),
            riderName == null ? "" : riderName.trim(),
            excludeRiderId,
            excludeRiderId
        );
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号已存在");
        }
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private DispatchRiderAuthSource loadRiderAuthSource(long riderId) {
        return jdbcTemplate.query(
            """
                SELECT
                    id,
                    phone,
                    current_openid,
                    wechat_open_id,
                    COALESCE(display_name, rider_name) AS display_name,
                    last_login_at
                FROM rider_profiles
                WHERE id = ?
                """,
            ps -> ps.setLong(1, riderId),
            rs -> {
                if (!rs.next()) {
                    throw new IllegalArgumentException("source rider not found");
                }
                return new DispatchRiderAuthSource(
                    rs.getLong("id"),
                    rs.getString("phone"),
                    rs.getString("current_openid"),
                    rs.getString("wechat_open_id"),
                    rs.getString("display_name"),
                    rs.getTimestamp("last_login_at")
                );
            }
        );
    }

    private Timestamp toTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        throw new IllegalArgumentException("unsupported timestamp value");
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    @FunctionalInterface
    interface AreaBindingUpdater {
        void update(String areaCode, String keywords, Long defaultRiderId, Long backupRiderId, String updatedBy);
    }

    private record DispatchRiderAuthSource(
        long riderId,
        String phone,
        String currentOpenid,
        String wechatOpenId,
        String displayName,
        Timestamp lastLoginAt
    ) {
    }
}
