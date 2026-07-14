package com.jzqs.app.maintenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    private static final DateTimeFormatter RANGE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NEXT_AUTO_RUN_LABEL = "每日 03:00";
    private static final int MAINTENANCE_LOG_RETENTION_DAYS = 90;
    private static final Set<String> BUSINESS_MODULE_KEYS = Set.of(
        "ORDER_HISTORY",
        "RECEIPT_RECORD",
        "WALLET_TRANSACTION"
    );
    private static final List<CleanupModuleDefinition> MODULE_DEFINITIONS = List.of(
        new CleanupModuleDefinition("ORDER_HISTORY", "订单历史", "ORDER_HISTORY_CLEANUP", 10),
        new CleanupModuleDefinition("DISPATCH_BATCH", "配送批次", "DISPATCH_BATCH_CLEANUP", 20),
        new CleanupModuleDefinition("RECEIPT_RECORD", "回执记录", "RECEIPT_RECORD_CLEANUP", 30),
        new CleanupModuleDefinition("DISPATCH_REASSIGNMENT", "区域调整记录", "DISPATCH_REASSIGNMENT_CLEANUP", 40),
        new CleanupModuleDefinition("ADDRESS_BINDING", "地址绑定", "ADDRESS_BINDING_CLEANUP", 50),
        new CleanupModuleDefinition("WALLET_TRANSACTION", "钱包流水", "WALLET_TRANSACTION_CLEANUP", 60)
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path uploadRootDir;

    public DataCleanupService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void scheduledCleanup() {
        log.info("开始执行定时数据清理任务");
        List<CleanupRule> autoRules = fetchBusinessConfiguredRules(true);
        if (autoRules.isEmpty()) {
            log.info("未开启任何自动清理模块，跳过本次定时任务");
            recordSkippedJob(
                "AUTO_DATA_CLEANUP",
                "SCHEDULED",
                "system",
                "自动清理已关闭",
                "当前未开启任何自动清理模块，本次任务跳过"
            );
            return;
        }
        runCleanupJob("AUTO_DATA_CLEANUP", "SCHEDULED", "system", autoRules);
    }

    @Transactional
    public String manualCleanup() {
        log.info("手动触发全部数据清理");
        runCleanupJob("MANUAL_DATA_CLEANUP", "ADMIN", "Admin", fetchBusinessConfiguredRules(false));
        return "全部清理任务已执行";
    }

    @Transactional
    public String manualCleanupByModule(String moduleKey) {
        CleanupRule rule = requireBusinessCleanupRule(moduleKey);
        log.info("手动触发模块清理: {}", rule.moduleKey());
        runCleanupJob(rule.jobType(), "ADMIN", "Admin", List.of(rule));
        return rule.moduleLabel() + " 清理已执行";
    }

    @Transactional
    public MaintenanceOverviewResponse updateCleanupSettings(MaintenanceCleanupSettingsUpdateRequest request) {
        List<MaintenanceCleanupSettingsUpdateRequest.MaintenanceCleanupRuleItem> rules = request == null || request.rules() == null
            ? List.of()
            : request.rules();
        for (MaintenanceCleanupSettingsUpdateRequest.MaintenanceCleanupRuleItem item : rules) {
            if (item == null || item.moduleKey() == null || item.moduleKey().isBlank()) {
                continue;
            }
            if (!isBusinessModule(item.moduleKey())) {
                continue;
            }
            CleanupModuleDefinition definition = requireDefinition(item.moduleKey());
            jdbcTemplate.update(
                """
                INSERT INTO maintenance_cleanup_settings (module_key, module_label, retention_value, retention_unit, auto_enabled, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    module_label = VALUES(module_label),
                    retention_value = VALUES(retention_value),
                    retention_unit = VALUES(retention_unit),
                    auto_enabled = VALUES(auto_enabled),
                    sort_order = VALUES(sort_order)
                """,
                definition.moduleKey(),
                definition.moduleLabel(),
                normalizeRetentionValue(item.retentionValue()),
                normalizeRetentionUnit(item.retentionUnit()),
                Boolean.TRUE.equals(item.autoEnabled()),
                definition.sortOrder()
            );
        }
        return fetchOverview();
    }

    public MaintenanceOverviewResponse fetchOverview() {
        return new MaintenanceOverviewResponse(
            fetchLatestLog("MANUAL_DATA_CLEANUP"),
            fetchLatestLog("AUTO_DATA_CLEANUP"),
            fetchLatestLog("CLOUD_RECEIPT_CLEANUP"),
            fetchLatestLog("CLOUD_STORAGE_SWEEP"),
            fetchCleanupRuleResponses(),
            NEXT_AUTO_RUN_LABEL
        );
    }

    public List<MaintenanceLogItemResponse> fetchRecentLogs(int limit) {
        return jdbcTemplate.query(
            """
            SELECT id, job_type, trigger_source, status, time_range_label, started_at, finished_at,
                   duration_ms, scanned_count, deleted_count, failed_count, message, error_detail, metadata_json
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
        pruneMaintenanceJobLogs();
        return fetchLatestLog(request.jobType());
    }

    private void runCleanupJob(String jobType, String triggerSource, String operatorName, List<CleanupRule> rules) {
        LocalDateTime startedAt = LocalDateTime.now();
        String timeRangeLabel = buildCleanupRangeLabel(rules);
        long logId = insertRunningLog(jobType, triggerSource, operatorName, timeRangeLabel, startedAt);
        try {
            List<MaintenanceCleanupModuleSummaryResponse> moduleSummaries = executeCleanupSteps(rules);
            CleanupStats stats = aggregateCleanupStats(moduleSummaries);
            updateLogSuccess(logId, startedAt, stats, moduleSummaries);
            pruneMaintenanceJobLogs();
            log.info("{} 执行完成: scanned={}, deleted={}, failed={}", jobType, stats.scannedCount(), stats.deletedCount(), stats.failedCount());
        } catch (Exception ex) {
            updateLogFailure(logId, startedAt, ex);
            pruneMaintenanceJobLogs();
            log.error("{} 执行失败", jobType, ex);
            throw ex;
        }
    }

    private List<MaintenanceCleanupRuleResponse> fetchCleanupRuleResponses() {
        return fetchBusinessConfiguredRules(false).stream()
            .map((rule) -> {
                ModuleExecutionSnapshot latestExecution = resolveLatestModuleExecution(rule);
                return new MaintenanceCleanupRuleResponse(
                    rule.moduleKey(),
                    rule.moduleLabel(),
                    rule.retentionValue(),
                    rule.retentionUnit(),
                    rule.autoEnabled(),
                    latestExecution == null ? "还没有执行记录" : latestExecution.summary(),
                    latestExecution == null || latestExecution.finishedAt() == null ? null : latestExecution.finishedAt().format(DISPLAY_FORMATTER),
                    latestExecution == null ? null : latestExecution.status()
                );
            })
            .toList();
    }

    private List<CleanupRule> fetchBusinessConfiguredRules(boolean autoOnly) {
        return fetchConfiguredRules(autoOnly).stream()
            .filter((rule) -> isBusinessModule(rule.moduleKey()))
            .toList();
    }

    private List<CleanupRule> fetchConfiguredRules(boolean autoOnly) {
        List<CleanupRule> configuredRules = jdbcTemplate.query(
            """
            SELECT module_key, module_label, retention_value, retention_unit, auto_enabled, sort_order
            FROM maintenance_cleanup_settings
            ORDER BY sort_order ASC, module_key ASC
            """,
            (rs, rowNum) -> {
                CleanupModuleDefinition definition = requireDefinition(rs.getString("module_key"));
                return new CleanupRule(
                    definition.moduleKey(),
                    definition.moduleLabel(),
                    definition.jobType(),
                    normalizeRetentionValue(rs.getObject("retention_value") == null ? null : rs.getInt("retention_value")),
                    normalizeRetentionUnit(rs.getString("retention_unit")),
                    rs.getBoolean("auto_enabled"),
                    rs.getInt("sort_order")
                );
            }
        );
        if (configuredRules.isEmpty()) {
            configuredRules = MODULE_DEFINITIONS.stream()
                .map((item) -> new CleanupRule(item.moduleKey(), item.moduleLabel(), item.jobType(), defaultRetentionValue(item.moduleKey()), "DAY", true, item.sortOrder()))
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return configuredRules.stream()
            .filter((rule) -> !autoOnly || rule.autoEnabled())
            .sorted(Comparator.comparingInt(CleanupRule::sortOrder))
            .toList();
    }

    private CleanupRule requireBusinessCleanupRule(String moduleKey) {
        if (!isBusinessModule(moduleKey)) {
            throw new IllegalArgumentException("当前模块不对商家开放手动清理: " + moduleKey);
        }
        return requireCleanupRule(moduleKey);
    }

    private CleanupRule requireCleanupRule(String moduleKey) {
        return fetchConfiguredRules(false).stream()
            .filter((rule) -> rule.moduleKey().equalsIgnoreCase(moduleKey))
            .findFirst()
            .orElseGet(() -> {
                CleanupModuleDefinition definition = requireDefinition(moduleKey);
                return new CleanupRule(
                    definition.moduleKey(),
                    definition.moduleLabel(),
                    definition.jobType(),
                    defaultRetentionValue(definition.moduleKey()),
                    "DAY",
                    true,
                    definition.sortOrder()
                );
            });
    }

    private boolean isBusinessModule(String moduleKey) {
        return moduleKey != null && BUSINESS_MODULE_KEYS.contains(moduleKey.trim().toUpperCase(Locale.ROOT));
    }

    private CleanupModuleDefinition requireDefinition(String moduleKey) {
        return MODULE_DEFINITIONS.stream()
            .filter((definition) -> definition.moduleKey().equalsIgnoreCase(moduleKey))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的清理模块: " + moduleKey));
    }

    private List<MaintenanceCleanupModuleSummaryResponse> executeCleanupSteps(List<CleanupRule> rules) {
        List<MaintenanceCleanupModuleSummaryResponse> summaries = new ArrayList<>();
        for (CleanupRule rule : rules) {
            summaries.add(executeModuleCleanup(rule));
        }
        return summaries;
    }

    private MaintenanceCleanupModuleSummaryResponse executeModuleCleanup(CleanupRule rule) {
        return switch (rule.moduleKey()) {
            case "ORDER_HISTORY" -> cleanupOldOrders(rule);
            case "DISPATCH_BATCH" -> cleanupOldDispatchData(rule);
            case "RECEIPT_RECORD" -> cleanupOldReceipts(rule);
            case "DISPATCH_REASSIGNMENT" -> cleanupOldReassignments(rule);
            case "ADDRESS_BINDING" -> cleanupUnusedAddressBindings(rule);
            case "WALLET_TRANSACTION" -> cleanupOldWalletTransactions(rule);
            default -> throw new IllegalArgumentException("不支持的清理模块: " + rule.moduleKey());
        };
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

    private void recordSkippedJob(
        String jobType,
        String triggerSource,
        String operatorName,
        String timeRangeLabel,
        String message
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            INSERT INTO maintenance_job_logs (
                job_type, trigger_source, status, time_range_label, started_at, finished_at,
                duration_ms, scanned_count, deleted_count, failed_count, message, error_detail,
                operator_name, metadata_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            jobType,
            triggerSource,
            "SUCCESS",
            timeRangeLabel,
            Timestamp.valueOf(startedAt),
            Timestamp.valueOf(startedAt),
            0L,
            0,
            0,
            0,
            message,
            null,
            operatorName,
            null
        );
        pruneMaintenanceJobLogs();
    }

    private void updateLogSuccess(
        long logId,
        LocalDateTime startedAt,
        CleanupStats stats,
        List<MaintenanceCleanupModuleSummaryResponse> moduleSummaries
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            UPDATE maintenance_job_logs
            SET status = ?, finished_at = ?, duration_ms = ?, scanned_count = ?, deleted_count = ?,
                failed_count = ?, message = ?, error_detail = NULL, metadata_json = ?
            WHERE id = ?
            """,
            stats.failedCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS",
            Timestamp.valueOf(finishedAt),
            Duration.between(startedAt, finishedAt).toMillis(),
            stats.scannedCount(),
            stats.deletedCount(),
            stats.failedCount(),
            stats.message(),
            serializeMetadata(moduleSummaries),
            logId
        );
    }

    private void updateLogFailure(long logId, LocalDateTime startedAt, Exception ex) {
        LocalDateTime finishedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            UPDATE maintenance_job_logs
            SET status = ?, finished_at = ?, duration_ms = ?, failed_count = ?, message = ?, error_detail = ?, metadata_json = NULL
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

    private CleanupStats aggregateCleanupStats(List<MaintenanceCleanupModuleSummaryResponse> moduleSummaries) {
        int scannedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        for (MaintenanceCleanupModuleSummaryResponse item : moduleSummaries) {
            scannedCount += item.scannedCount();
            deletedCount += item.deletedCount();
            failedCount += item.failedCount();
        }
        String message = deletedCount > 0
            ? "已完成 " + moduleSummaries.size() + " 个板块检查，累计清理 " + deletedCount + " 条记录"
            : "已完成 " + moduleSummaries.size() + " 个板块检查，本次无需清理";
        return new CleanupStats(scannedCount, deletedCount, failedCount, message);
    }

    private MaintenanceLogItemResponse fetchLatestLog(String jobType) {
        List<MaintenanceLogItemResponse> items = jdbcTemplate.query(
            """
            SELECT id, job_type, trigger_source, status, time_range_label, started_at, finished_at,
                   duration_ms, scanned_count, deleted_count, failed_count, message, error_detail, metadata_json
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

    private ModuleExecutionSnapshot resolveLatestModuleExecution(CleanupRule rule) {
        ModuleExecutionSnapshot directExecution = toModuleExecutionSnapshot(fetchLatestLog(rule.jobType()), rule.moduleKey());
        ModuleExecutionSnapshot aggregateExecution = fetchLatestAggregateModuleExecution(rule.moduleKey());
        if (directExecution == null) {
            return aggregateExecution;
        }
        if (aggregateExecution == null) {
            return directExecution;
        }
        return latestModuleExecution(directExecution, aggregateExecution);
    }

    private ModuleExecutionSnapshot fetchLatestAggregateModuleExecution(String moduleKey) {
        List<MaintenanceLogItemResponse> items = jdbcTemplate.query(
            """
            SELECT id, job_type, trigger_source, status, time_range_label, started_at, finished_at,
                   duration_ms, scanned_count, deleted_count, failed_count, message, error_detail, metadata_json
            FROM maintenance_job_logs
            WHERE job_type IN ('AUTO_DATA_CLEANUP', 'MANUAL_DATA_CLEANUP')
            ORDER BY started_at DESC, id DESC
            LIMIT 20
            """,
            maintenanceLogRowMapper()
        );
        for (MaintenanceLogItemResponse item : items) {
            ModuleExecutionSnapshot snapshot = toModuleExecutionSnapshot(item, moduleKey);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    private ModuleExecutionSnapshot toModuleExecutionSnapshot(MaintenanceLogItemResponse item, String moduleKey) {
        if (item == null) {
            return null;
        }
        if (moduleKey.equalsIgnoreCase(item.moduleKey())) {
            return new ModuleExecutionSnapshot(item.status(), item.message(), item.startedAt(), item.finishedAt());
        }
        for (MaintenanceCleanupModuleSummaryResponse summary : item.moduleSummaries()) {
            if (moduleKey.equalsIgnoreCase(summary.moduleKey())) {
                return new ModuleExecutionSnapshot(item.status(), summary.summary(), item.startedAt(), item.finishedAt());
            }
        }
        return null;
    }

    private ModuleExecutionSnapshot latestModuleExecution(
        ModuleExecutionSnapshot left,
        ModuleExecutionSnapshot right
    ) {
        LocalDateTime leftTime = executionTime(left);
        LocalDateTime rightTime = executionTime(right);
        if (leftTime == null) {
            return right;
        }
        if (rightTime == null) {
            return left;
        }
        return rightTime.isAfter(leftTime) ? right : left;
    }

    private LocalDateTime executionTime(ModuleExecutionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.finishedAt() != null ? snapshot.finishedAt() : snapshot.startedAt();
    }

    private RowMapper<MaintenanceLogItemResponse> maintenanceLogRowMapper() {
        return (rs, rowNum) -> {
            String jobType = rs.getString("job_type");
            return new MaintenanceLogItemResponse(
                rs.getLong("id"),
                jobType,
                resolveModuleKeyByJobType(jobType),
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
                rs.getString("error_detail"),
                deserializeModuleSummaries(rs.getString("metadata_json"))
            );
        };
    }

    private String resolveModuleKeyByJobType(String jobType) {
        return MODULE_DEFINITIONS.stream()
            .filter((definition) -> definition.jobType().equalsIgnoreCase(jobType))
            .map(CleanupModuleDefinition::moduleKey)
            .findFirst()
            .orElse(null);
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupOldOrders(CleanupRule rule) {
        LocalDate cutoffDate = cutoffDate(rule);
        int mealSlotCount = jdbcTemplate.update(
            """
            DELETE mso
            FROM meal_slot_orders mso
            JOIN (
                SELECT candidate.id
                FROM (
                    SELECT mso_inner.id
                    FROM meal_slot_orders mso_inner
                    JOIN daily_orders do ON do.id = mso_inner.daily_order_id
                    WHERE do.serve_date < ?
                      AND mso_inner.status IN ('DELIVERED', 'CANCELLED')
                    LIMIT 1000
                ) candidate
            ) targets ON targets.id = mso.id
            """,
            cutoffDate
        );
        int dailyOrderCount = jdbcTemplate.update(
            """
            DELETE FROM daily_orders
            WHERE serve_date < ?
              AND status IN ('DELIVERED', 'CANCELLED')
              AND id NOT IN (SELECT DISTINCT daily_order_id FROM meal_slot_orders)
            LIMIT 1000
            """,
            cutoffDate
        );
        int deletedCount = mealSlotCount + dailyOrderCount;
        if (deletedCount > 0) {
            log.info("清理订单历史: meal_slot_orders={}, daily_orders={}", mealSlotCount, dailyOrderCount);
        }
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            deletedCount,
            deletedCount,
            0,
            "订单<" + cutoffDate,
            deletedCount > 0 ? "扫描 " + deletedCount + " 条订单历史，清理 " + deletedCount + " 条" : "订单历史本次无需清理"
        );
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupOldDispatchData(CleanupRule rule) {
        LocalDate cutoffDate = cutoffDate(rule);
        int batchItemCount = jdbcTemplate.update(
            """
            DELETE dbi
            FROM dispatch_batch_items dbi
            JOIN (
                SELECT candidate.id
                FROM (
                    SELECT dbi_inner.id
                    FROM dispatch_batch_items dbi_inner
                    JOIN dispatch_batches db ON db.id = dbi_inner.batch_id
                    WHERE db.serve_date < ?
                    LIMIT 1000
                ) candidate
            ) targets ON targets.id = dbi.id
            """,
            cutoffDate
        );
        int batchCount = jdbcTemplate.update(
            """
            DELETE FROM dispatch_batches
            WHERE serve_date < ?
              AND id NOT IN (SELECT DISTINCT batch_id FROM dispatch_batch_items)
            LIMIT 1000
            """,
            cutoffDate
        );
        int assignmentCount = jdbcTemplate.update(
            """
            DELETE da
            FROM dispatch_assignments da
            JOIN (
                SELECT candidate.id
                FROM (
                    SELECT da_inner.id
                    FROM dispatch_assignments da_inner
                    JOIN meal_slot_orders mso ON mso.id = da_inner.meal_slot_order_id
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE do.serve_date < ?
                      AND da_inner.status = 'DELIVERED'
                    LIMIT 1000
                ) candidate
            ) targets ON targets.id = da.id
            """,
            cutoffDate
        );
        int deletedCount = batchItemCount + batchCount + assignmentCount;
        if (deletedCount > 0) {
            log.info("清理配送批次: batch_items={}, batches={}, assignments={}", batchItemCount, batchCount, assignmentCount);
        }
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            deletedCount,
            deletedCount,
            0,
            "配送<" + cutoffDate,
            deletedCount > 0 ? "扫描 " + deletedCount + " 条配送批次数据，清理 " + deletedCount + " 条" : "配送批次本次无需清理"
        );
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupOldReceipts(CleanupRule rule) {
        LocalDateTime cutoffTime = cutoffDateTime(rule);
        List<String> receiptPathsToCleanup = jdbcTemplate.queryForList(
            """
            SELECT receipt_url
            FROM delivery_receipts
            WHERE delivered_at < ?
              AND (
                  receipt_url LIKE '/uploads/%'
                  OR receipt_url LIKE 'http://%/uploads/%'
                  OR receipt_url LIKE 'https://%/uploads/%'
                  OR (
                      COALESCE(receipt_url, '') = ''
                      AND COALESCE(receipt_note, '') <> ''
                  )
              )
            LIMIT 1000
            """,
            String.class,
            cutoffTime
        );
        int deletedLocalFiles = deleteLocalReceiptFiles(receiptPathsToCleanup);
        int cleanedCount = jdbcTemplate.update(
            """
            UPDATE delivery_receipts
            SET receipt_url = '',
                receipt_note = '',
                visible_to_customer = FALSE
            WHERE delivered_at < ?
              AND (
                  receipt_url LIKE '/uploads/%'
                  OR receipt_url LIKE 'http://%/uploads/%'
                  OR receipt_url LIKE 'https://%/uploads/%'
                  OR (
                      COALESCE(receipt_url, '') = ''
                      AND COALESCE(receipt_note, '') <> ''
                  )
              )
            LIMIT 1000
            """,
            cutoffTime
        );
        if (cleanedCount > 0 || deletedLocalFiles > 0) {
            log.info("清理本地回执内容: records={}, local_files={}", cleanedCount, deletedLocalFiles);
        }
        int scannedCount = receiptPathsToCleanup.size();
        String summary = cleanedCount > 0 || deletedLocalFiles > 0
            ? "扫描 " + scannedCount + " 条本地回执记录，清理 " + cleanedCount + " 条回执内容，落盘图片清理 " + deletedLocalFiles + " 张"
            : "本地回执记录本次无需清理";
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            scannedCount,
            cleanedCount,
            0,
            "回执<" + cutoffTime.format(RANGE_FORMATTER),
            summary
        );
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupOldReassignments(CleanupRule rule) {
        LocalDateTime cutoffTime = cutoffDateTime(rule);
        int deletedCount = jdbcTemplate.update(
            """
            DELETE FROM dispatch_reassignments
            WHERE created_at < ?
            LIMIT 1000
            """,
            cutoffTime
        );
        if (deletedCount > 0) {
            log.info("清理区域调整记录: {}", deletedCount);
        }
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            deletedCount,
            deletedCount,
            0,
            "区域调整<" + cutoffTime.format(RANGE_FORMATTER),
            deletedCount > 0 ? "扫描 " + deletedCount + " 条区域调整记录，清理 " + deletedCount + " 条" : "区域调整记录本次无需清理"
        );
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupUnusedAddressBindings(CleanupRule rule) {
        LocalDate cutoffDate = cutoffDate(rule);
        // rider_address_bindings 没有 last_used_at，updated_at 才是绑定被刷新/人工确认后的有效最近活动时间。
        int deletedCount = jdbcTemplate.update(
            """
            DELETE FROM rider_address_bindings
            WHERE updated_at < ?
            LIMIT 1000
            """,
            cutoffDate
        );
        if (deletedCount > 0) {
            log.info("清理地址绑定: {}", deletedCount);
        }
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            deletedCount,
            deletedCount,
            0,
            "地址绑定<" + cutoffDate,
            deletedCount > 0 ? "扫描 " + deletedCount + " 条地址绑定，清理 " + deletedCount + " 条" : "地址绑定本次无需清理"
        );
    }

    private MaintenanceCleanupModuleSummaryResponse cleanupOldWalletTransactions(CleanupRule rule) {
        LocalDateTime cutoffTime = cutoffDateTime(rule);
        int deletedCount = jdbcTemplate.update(
            """
            DELETE FROM wallet_transactions
            WHERE created_at < ?
            LIMIT 1000
            """,
            cutoffTime
        );
        if (deletedCount > 0) {
            log.info("清理钱包流水: {}", deletedCount);
        }
        return new MaintenanceCleanupModuleSummaryResponse(
            rule.moduleKey(),
            rule.moduleLabel(),
            deletedCount,
            deletedCount,
            0,
            "钱包流水<" + cutoffTime.format(RANGE_FORMATTER),
            deletedCount > 0 ? "扫描 " + deletedCount + " 条钱包流水，清理 " + deletedCount + " 条" : "钱包流水本次无需清理"
        );
    }

    private String buildCleanupRangeLabel(List<CleanupRule> rules) {
        Map<String, String> labels = new HashMap<>();
        for (CleanupRule rule : rules) {
            if ("DAY".equals(rule.retentionUnit())) {
                labels.put(rule.moduleKey(), rule.moduleLabel() + "<" + cutoffDate(rule));
            } else {
                labels.put(rule.moduleKey(), rule.moduleLabel() + "<" + cutoffDateTime(rule).format(RANGE_FORMATTER));
            }
        }
        return labels.values().stream().collect(Collectors.joining("，"));
    }

    private LocalDate cutoffDate(CleanupRule rule) {
        LocalDateTime cutoff = cutoffDateTime(rule);
        return cutoff.toLocalDate();
    }

    private LocalDateTime cutoffDateTime(CleanupRule rule) {
        return switch (rule.retentionUnit()) {
            case "HOUR" -> LocalDateTime.now().minusHours(rule.retentionValue());
            default -> LocalDate.now().minusDays(rule.retentionValue()).atTime(LocalTime.MIN);
        };
    }

    private int defaultRetentionValue(String moduleKey) {
        return switch (moduleKey) {
            case "ORDER_HISTORY" -> 30;
            case "DISPATCH_BATCH" -> 14;
            case "RECEIPT_RECORD" -> 1;
            case "DISPATCH_REASSIGNMENT" -> 14;
            case "ADDRESS_BINDING" -> 90;
            case "WALLET_TRANSACTION" -> 90;
            default -> 30;
        };
    }

    private void pruneMaintenanceJobLogs() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(MAINTENANCE_LOG_RETENTION_DAYS);
        int deletedCount = jdbcTemplate.update(
            """
            DELETE FROM maintenance_job_logs
            WHERE started_at < ?
            LIMIT 5000
            """,
            Timestamp.valueOf(cutoffTime)
        );
        if (deletedCount > 0) {
            log.info("清理维护执行日志: {}", deletedCount);
        }
    }

    private int normalizeRetentionValue(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private String normalizeRetentionUnit(String value) {
        String normalized = value == null ? "DAY" : value.trim().toUpperCase(Locale.ROOT);
        return "HOUR".equals(normalized) ? "HOUR" : "DAY";
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

    private List<MaintenanceCleanupModuleSummaryResponse> deserializeModuleSummaries(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<List<MaintenanceCleanupModuleSummaryResponse>>() {
            });
        } catch (Exception ex) {
            log.warn("解析维护日志 metadata 失败", ex);
            return List.of();
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int deleteLocalReceiptFiles(List<String> localReceiptPaths) {
        int deletedCount = 0;
        for (String receiptPath : localReceiptPaths) {
            String managedUploadPath = resolveManagedUploadPath(receiptPath);
            if (managedUploadPath == null) {
                continue;
            }
            Path relativePath = Path.of(managedUploadPath.substring("/uploads/".length()));
            Path filePath = uploadRootDir.resolve(relativePath).normalize();
            if (!filePath.startsWith(uploadRootDir)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(filePath)) {
                    deletedCount++;
                }
            } catch (IOException ex) {
                log.warn("删除本地回执图片失败: {}", filePath, ex);
            }
        }
        return deletedCount;
    }

    private String resolveManagedUploadPath(String receiptPath) {
        if (receiptPath == null || receiptPath.isBlank()) {
            return null;
        }
        String normalized = receiptPath.trim();
        if (normalized.startsWith("/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                String path = URI.create(normalized).getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    return path;
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record CleanupModuleDefinition(
        String moduleKey,
        String moduleLabel,
        String jobType,
        int sortOrder
    ) {
    }

    private record CleanupRule(
        String moduleKey,
        String moduleLabel,
        String jobType,
        int retentionValue,
        String retentionUnit,
        boolean autoEnabled,
        int sortOrder
    ) {
    }

    private record CleanupStats(
        int scannedCount,
        int deletedCount,
        int failedCount,
        String message
    ) {
    }

    private record ModuleExecutionSnapshot(
        String status,
        String summary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
    ) {
    }
}
