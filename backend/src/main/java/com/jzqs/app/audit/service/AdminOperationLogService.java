package com.jzqs.app.audit.service;

import com.jzqs.app.audit.api.AdminOperationLogResponse;
import com.jzqs.app.common.api.PageResponse;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationLogService {
    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogService.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 200;
    /** 操作日志保留天数：单条日志体积很小（几百字节），但会持续累积，超期自动清理 */
    private static final int RETENTION_DAYS = 180;

    private final JdbcTemplate jdbcTemplate;
    private final AdminOperationLogLabelService labelService;

    public AdminOperationLogService(JdbcTemplate jdbcTemplate, AdminOperationLogLabelService labelService) {
        this.jdbcTemplate = jdbcTemplate;
        this.labelService = labelService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOperationLogResponse> list(
        Integer page,
        Integer pageSize,
        Long operatorId,
        String operatorName,
        String module,
        String status,
        String startDate,
        String endDate
    ) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (operatorId != null) {
            where.append(" AND operator_id = ?");
            params.add(operatorId);
        }
        if (operatorName != null && !operatorName.trim().isEmpty()) {
            where.append(" AND (operator_name LIKE ? OR operator_phone LIKE ?)");
            String keyword = "%" + operatorName.trim() + "%";
            params.add(keyword);
            params.add(keyword);
        }
        if (module != null && !module.trim().isEmpty()) {
            where.append(" AND module = ?");
            params.add(module.trim().toUpperCase());
        }
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
        LocalDateTime startDateTime = parseStartDate(startDate);
        if (startDateTime != null) {
            where.append(" AND created_at >= ?");
            params.add(Timestamp.valueOf(startDateTime));
        }
        LocalDateTime endDateTime = parseEndDate(endDate);
        if (endDateTime != null) {
            where.append(" AND created_at < ?");
            params.add(Timestamp.valueOf(endDateTime));
        }

        String whereClause = where.toString();
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_operation_logs" + whereClause,
            Long.class,
            params.toArray()
        );
        long totalCount = total == null ? 0 : total;

        List<Object> queryParams = new ArrayList<>(params);
        // SQL 为 LIMIT ? OFFSET ?，参数顺序必须与占位符一致：先 pageSize(limit)，后 offset
        queryParams.add(currentPageSize);
        queryParams.add((currentPage - 1) * currentPageSize);
        List<AdminOperationLogRawRow> rawRows = jdbcTemplate.query(
            "SELECT id, operator_id, operator_name, operator_phone, operator_role, module, action, "
                + "http_method, request_path, request_summary, status, error_message, duration_ms, client_ip, created_at "
                + "FROM admin_operation_logs" + whereClause
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
            (rs, rowNum) -> new AdminOperationLogRawRow(
                rs.getLong("id"),
                (Long) rs.getObject("operator_id"),
                rs.getString("operator_name"),
                rs.getString("operator_phone"),
                rs.getString("operator_role"),
                rs.getString("module"),
                rs.getString("action"),
                rs.getString("http_method"),
                rs.getString("request_path"),
                rs.getString("request_summary"),
                rs.getString("status"),
                rs.getString("error_message"),
                (Long) rs.getObject("duration_ms"),
                rs.getString("client_ip"),
                rs.getTimestamp("created_at")
            ),
            queryParams.toArray()
        );

        // 批量解析本页日志的操作对象（客户/骑手/后台账号/订单），转成中文标签
        List<AdminOperationLogLabelService.LogRef> logRefs = rawRows.stream()
            .map(row -> new AdminOperationLogLabelService.LogRef(row.requestPath(), row.requestSummary()))
            .toList();
        AdminOperationLogLabelService.NameCache nameCache = labelService.loadNameCache(logRefs);
        List<AdminOperationLogResponse> items = new ArrayList<>(rawRows.size());
        for (AdminOperationLogRawRow row : rawRows) {
            items.add(new AdminOperationLogResponse(
                row.id(),
                row.operatorId(),
                row.operatorName(),
                row.operatorPhone(),
                row.operatorRole(),
                row.module(),
                row.action(),
                row.httpMethod(),
                row.requestPath(),
                row.requestSummary(),
                row.status(),
                row.errorMessage(),
                row.durationMs(),
                row.clientIp(),
                formatTimestamp(row.createdAt()),
                labelService.moduleLabel(row.module()),
                labelService.actionLabel(row.module(), row.action()),
                labelService.targetLabel(row.requestPath(), row.requestSummary(), nameCache),
                labelService.detailLabel(row.action(), row.requestSummary())
            ));
        }

        return PageResponse.of(items, currentPage, currentPageSize, (int) totalCount);
    }

    /**
     * 每日 03:10 清理超期操作日志，避免表无限膨胀。
     * 单条日志仅几百字节，正常量级下 180 天约几十 MB；DELETE 带 LIMIT 分批执行，避免长事务锁表。
     */
    @Scheduled(cron = "0 10 3 * * ?", zone = "Asia/Shanghai")
    @Transactional
    public void purgeExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deletedCount = jdbcTemplate.update(
            "DELETE FROM admin_operation_logs WHERE created_at < ? LIMIT 1000",
            Timestamp.valueOf(cutoff)
        );
        if (deletedCount > 0) {
            log.info("操作日志自动清理：删除 {} 天前的日志 {} 条", RETENTION_DAYS, deletedCount);
        }
    }

    private record AdminOperationLogRawRow(
        Long id,
        Long operatorId,
        String operatorName,
        String operatorPhone,
        String operatorRole,
        String module,
        String action,
        String httpMethod,
        String requestPath,
        String requestSummary,
        String status,
        String errorMessage,
        Long durationMs,
        String clientIp,
        Timestamp createdAt
    ) {
    }

    private LocalDateTime parseStartDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime parseEndDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay();
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATETIME_FORMATTER);
    }
}
