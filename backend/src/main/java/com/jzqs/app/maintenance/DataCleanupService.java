package com.jzqs.app.maintenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据清理服务
 * 定期清理历史数据，避免数据库无限增长
 */
@Service
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    private static final DateTimeFormatter RANGE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataCleanupService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 每天凌晨3点执行数据清理
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void scheduledCleanup() {
        log.info("开始执行定时数据清理任务");
        runCleanupJob("AUTO_DATA_CLEANUP", "SCHEDULED", "system");
    }

    /**
     * 手动触发清理（用于测试或紧急清理）
     */
    @Transactional
    public void manualCleanup() {
        log.info("手动触发数据清理");
        runCleanupJob("MANUAL_DATA_CLEANUP", "ADMIN", "Admin");
    }

    public MaintenanceOverviewResponse fetchOverview() {
        return new MaintenanceOverviewResponse(
            fetchLatestLog("MANUAL_DATA_CLEANUP"),
            fetchLatestLog("AUTO_DATA_CLEANUP"),
            fetchLatestLog("CLOUD_RECEIPT_CLEANUP"),
            fetchLatestLog("CLOUD_STORAGE_SWEEP")
        );
    }

    public List<MaintenanceLogItemResponse> fetchRecentLogs(int limit) {
        return jdbcTemplate.query(
            """
            SELECT id, job_type, trigger_source, status, time_range_label, started_at, finished_at,
                   duration_ms, scanned_count, deleted_count, failed_count, message, error_detail
            FROM maintenance_job_logs
            ORDER BY started_at DESC, id DESC
            LIMIT ?
            """,
            maintenanceLogRowMapper(),
            limit
        );
    }

    public MaintenanceLogItemResponse recordCloudJob(CloudMaintenanceJobReportRequest request) {
        LocalDateTime startedAt = request.startedAt() == null ? LocalDateTime.now() : request.startedAt();
        LocalDateTime finishedAt = request.finishedAt();
        Long durationMs = finishedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
        String metadataJson = serializeMetadata(request.metadata());
        String timeRangeLabel = request.timeRangeLabel() == null || request.timeRangeLabel().isBlank()
            ? "云清理任务执行"
            : request.timeRangeLabel();
        String message = request.message() == null || request.message().isBlank()
            ? "云清理任务执行完成"
            : request.message();

        jdbcTemplate.update(
            """
            INSERT INTO maintenance_job_logs (
                job_type, trigger_source, status, time_range_label, started_at, finished_at,
                duration_ms, scanned_count, deleted_count, failed_count, message, error_detail,
                operator_name, metadata_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            request.jobType(),
            "WECHAT_CLOUDFUNCTION",
            request.status(),
            timeRangeLabel,
            Timestamp.valueOf(startedAt),
            finishedAt == null ? null : Timestamp.valueOf(finishedAt),
            durationMs,
            safeInt(request.scannedCount()),
            safeInt(request.deletedCount()),
            safeInt(request.failedCount()),
            message,
            request.errorDetail(),
            "cloudfunction",
            metadataJson
        );

        return fetchLatestLog(request.jobType());
    }

    private void runCleanupJob(String jobType, String triggerSource, String operatorName) {
        LocalDateTime startedAt = LocalDateTime.now();
        long logId = insertRunningLog(jobType, triggerSource, operatorName, buildCleanupRangeLabel(), startedAt);
        try {
            CleanupStats stats = executeCleanupSteps();
            updateLogSuccess(logId, startedAt, stats);
            log.info("{} 执行完成: scanned={}, deleted={}", jobType, stats.scannedCount(), stats.deletedCount());
        } catch (Exception ex) {
            updateLogFailure(logId, startedAt, ex);
            log.error("{} 执行失败", jobType, ex);
            throw ex;
        }
    }

    private CleanupStats executeCleanupSteps() {
        int orderCount = cleanupOldOrders();
        int dispatchCount = cleanupOldDispatchData();
        int receiptCount = cleanupOldReceipts();
        int notificationCount = cleanupOldNotifications();
        int reassignmentCount = cleanupOldReassignments();
        int bindingCount = cleanupUnusedAddressBindings();
        int walletCount = cleanupOldWalletTransactions();
        int deletedCount = orderCount + dispatchCount + receiptCount + notificationCount + reassignmentCount + bindingCount + walletCount;
        return new CleanupStats(deletedCount, deletedCount, 0, buildCleanupMessage(deletedCount));
    }

    private long insertRunningLog(String jobType, String triggerSource, String operatorName, String timeRangeLabel, LocalDateTime startedAt) {
        jdbcTemplate.update(
            """
            INSERT INTO maintenance_job_logs (
                job_type, trigger_source, status, time_range_label, started_at, message, operator_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            jobType,
            triggerSource,
            "RUNNING",
            timeRangeLabel,
            Timestamp.valueOf(startedAt),
            "维护任务执行中",
            operatorName
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private void updateLogSuccess(long logId, LocalDateTime startedAt, CleanupStats stats) {
        LocalDateTime finishedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            UPDATE maintenance_job_logs
            SET status = ?, finished_at = ?, duration_ms = ?, scanned_count = ?, deleted_count = ?,
                failed_count = ?, message = ?, error_detail = NULL
            WHERE id = ?
            """,
            stats.failedCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS",
            Timestamp.valueOf(finishedAt),
            Duration.between(startedAt, finishedAt).toMillis(),
            stats.scannedCount(),
            stats.deletedCount(),
            stats.failedCount(),
            stats.message(),
            logId
        );
    }

    private void updateLogFailure(long logId, LocalDateTime startedAt, Exception ex) {
        LocalDateTime finishedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            UPDATE maintenance_job_logs
            SET status = ?, finished_at = ?, duration_ms = ?, failed_count = ?, message = ?, error_detail = ?
            WHERE id = ?
            """,
            "FAILED",
            Timestamp.valueOf(finishedAt),
            Duration.between(startedAt, finishedAt).toMillis(),
            1,
            "维护任务执行失败",
            ex.getMessage(),
            logId
        );
    }

    private MaintenanceLogItemResponse fetchLatestLog(String jobType) {
        List<MaintenanceLogItemResponse> items = jdbcTemplate.query(
            """
            SELECT id, job_type, trigger_source, status, time_range_label, started_at, finished_at,
                   duration_ms, scanned_count, deleted_count, failed_count, message, error_detail
            FROM maintenance_job_logs
            WHERE job_type = ?
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """,
            maintenanceLogRowMapper(),
            jobType
        );
        return items.isEmpty() ? null : items.get(0);
    }

    private RowMapper<MaintenanceLogItemResponse> maintenanceLogRowMapper() {
        return (rs, rowNum) -> new MaintenanceLogItemResponse(
            rs.getLong("id"),
            rs.getString("job_type"),
            rs.getString("trigger_source"),
            rs.getString("status"),
            rs.getString("time_range_label"),
            toLocalDateTime(rs, "started_at"),
            toLocalDateTime(rs, "finished_at"),
            rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"),
            rs.getInt("scanned_count"),
            rs.getInt("deleted_count"),
            rs.getInt("failed_count"),
            rs.getString("message"),
            rs.getString("error_detail")
        );
    }

    /**
     * 清理30天前的已完成订单
     */
    private int cleanupOldOrders() {
        LocalDate cutoffDate = LocalDate.now().minusDays(30);

        int mealSlotCount = jdbcTemplate.update("""
            DELETE FROM meal_slot_orders
            WHERE id IN (
                SELECT mso.id FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.serve_date < ?
                  AND mso.status IN ('DELIVERED', 'CANCELLED')
                LIMIT 1000
            )
            """, cutoffDate);

        int dailyOrderCount = jdbcTemplate.update("""
            DELETE FROM daily_orders
            WHERE serve_date < ?
              AND status IN ('DELIVERED', 'CANCELLED')
              AND id NOT IN (SELECT DISTINCT daily_order_id FROM meal_slot_orders)
            LIMIT 1000
            """, cutoffDate);

        if (mealSlotCount > 0 || dailyOrderCount > 0) {
            log.info("清理订单数据: meal_slot_orders={}, daily_orders={}", mealSlotCount, dailyOrderCount);
        }
        return mealSlotCount + dailyOrderCount;
    }

    /**
     * 清理14天前的配送数据
     */
    private int cleanupOldDispatchData() {
        LocalDate cutoffDate = LocalDate.now().minusDays(14);

        int batchItemCount = jdbcTemplate.update("""
            DELETE FROM dispatch_batch_items
            WHERE id IN (
                SELECT dbi.id FROM dispatch_batch_items dbi
                JOIN dispatch_batches db ON db.id = dbi.batch_id
                WHERE db.serve_date < ?
                LIMIT 1000
            )
            """, cutoffDate);

        int batchCount = jdbcTemplate.update("""
            DELETE FROM dispatch_batches
            WHERE serve_date < ?
              AND id NOT IN (SELECT DISTINCT batch_id FROM dispatch_batch_items)
            LIMIT 1000
            """, cutoffDate);

        int assignmentCount = jdbcTemplate.update("""
            DELETE FROM dispatch_assignments
            WHERE id IN (
                SELECT da.id FROM dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.serve_date < ?
                  AND da.status = 'DELIVERED'
                LIMIT 1000
            )
            """, cutoffDate);

        if (batchItemCount > 0 || batchCount > 0 || assignmentCount > 0) {
            log.info("清理配送数据: batch_items={}, batches={}, assignments={}", batchItemCount, batchCount, assignmentCount);
        }
        return batchItemCount + batchCount + assignmentCount;
    }

    /**
     * 清理14天前的回执照片记录
     * 注意：云存储图片通过生命周期规则自动删除（1天后），这里只清理数据库记录
     */
    private int cleanupOldReceipts() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(14);

        int count = jdbcTemplate.update("""
            DELETE FROM delivery_receipts
            WHERE delivered_at < ?
            LIMIT 1000
            """, cutoffTime);

        if (count > 0) {
            log.info("清理回执记录: {}", count);
            log.info("提示：云存储图片已通过生命周期规则自动删除（上传1天后）");
        }
        return count;
    }

    /**
     * 清理14天前的通知日志
     */
    private int cleanupOldNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(14);

        int count = jdbcTemplate.update("""
            DELETE FROM notification_logs
            WHERE sent_at < ?
            LIMIT 1000
            """, cutoffTime);

        if (count > 0) {
            log.info("清理通知日志: {}", count);
        }
        return count;
    }

    /**
     * 清理14天前的区域调整记录
     */
    private int cleanupOldReassignments() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(14);

        int count = jdbcTemplate.update("""
            DELETE FROM dispatch_reassignments
            WHERE created_at < ?
            LIMIT 1000
            """, cutoffTime);

        if (count > 0) {
            log.info("清理区域调整记录: {}", count);
        }
        return count;
    }

    /**
     * 清理90天未使用的地址绑定
     */
    private int cleanupUnusedAddressBindings() {
        LocalDate cutoffDate = LocalDate.now().minusDays(90);

        int count = jdbcTemplate.update("""
            DELETE FROM rider_address_bindings
            WHERE last_used_at < ?
            LIMIT 1000
            """, cutoffDate);

        if (count > 0) {
            log.info("清理地址绑定: {}", count);
        }
        return count;
    }

    /**
     * 清理90天前的钱包流水
     */
    private int cleanupOldWalletTransactions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90);

        int count = jdbcTemplate.update("""
            DELETE FROM wallet_transactions
            WHERE created_at < ?
            LIMIT 1000
            """, cutoffTime);

        if (count > 0) {
            log.info("清理钱包流水: {}", count);
        }
        return count;
    }

    private String buildCleanupRangeLabel() {
        LocalDateTime now = LocalDateTime.now();
        return "订单<" + LocalDate.now().minusDays(30)
            + "，配送/回执/通知/区域调整<" + now.minusDays(14).format(RANGE_FORMATTER)
            + "，地址绑定/钱包流水<" + LocalDate.now().minusDays(90);
    }

    private String buildCleanupMessage(int deletedCount) {
        return deletedCount > 0
            ? "数据清理执行成功，累计清理 " + deletedCount + " 条记录"
            : "数据清理执行成功，本次无需清理";
    }

    private String serializeMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("序列化维护日志 metadata 失败", ex);
            return null;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record CleanupStats(
        int scannedCount,
        int deletedCount,
        int failedCount,
        String message
    ) {
    }
}
