package com.jzqs.app.dispatch.service.impl;

import com.jzqs.app.dispatch.api.DispatchManagedRiderResponse;
import com.jzqs.app.dispatch.api.DispatchRiderActivateResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthBindingResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthTakeoverResponse;
import com.jzqs.app.dispatch.api.DispatchRiderAuthUnbindResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import com.jzqs.app.dispatch.api.DispatchRiderStatusResponse;
import com.jzqs.app.dispatch.api.PendingRiderResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
class DispatchRiderAdminModule {
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
            rs.getString("current_openid")
        ), args.toArray());
    }

    DispatchRiderProfileUpsertResponse createRider(
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String employmentStatus,
        String updatedBy,
        AreaBindingUpdater areaBindingUpdater
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
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
                    auth_status,
                    assigned_at,
                    assigned_by,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            riderName,
            displayName,
            phone,
            employmentStatus,
            normalizedAreaCode,
            0,
            null,
            active ? "ACTIVE" : "DISABLED",
            Timestamp.valueOf(now),
            updatedBy,
            Timestamp.valueOf(now)
        );
        if (normalizedAreaCode != null && active) {
            areaBindingUpdater.update(normalizedAreaCode, null, riderId, null, updatedBy);
        }
        return new DispatchRiderProfileUpsertResponse(
            riderId,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            active ? "ACTIVE" : "DISABLED"
        );
    }

    DispatchRiderProfileUpsertResponse updateRiderProfile(
        long riderId,
        String riderName,
        String displayName,
        String phone,
        String areaCode,
        String updatedBy
    ) {
        String normalizedAreaCode = areaCode == null || areaCode.isBlank() ? null : areaCode.trim();
        jdbcTemplate.update(
            """
                UPDATE rider_profiles
                SET rider_name = ?,
                    display_name = ?,
                    phone = ?,
                    default_area_code = ?,
                    assigned_by = ?,
                    assigned_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            riderName,
            displayName,
            phone,
            normalizedAreaCode,
            updatedBy,
            riderId
        );
        jdbcTemplate.update(
            "UPDATE dispatch_assignments SET rider_name = ? WHERE rider_profile_id = ?",
            riderName, riderId
        );
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
            riderStatus
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
        return new DispatchRiderStatusResponse(
            riderId,
            "DISABLED"
        );
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
