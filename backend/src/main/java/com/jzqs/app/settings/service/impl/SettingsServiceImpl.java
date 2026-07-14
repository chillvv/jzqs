package com.jzqs.app.settings.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.dispatch.api.DispatchOrderReorderItemRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionItemResponse;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionRequest;
import com.jzqs.app.dispatch.api.DispatchRouteSuggestionResponse;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.dispatch.service.route.DispatchAiJobLogModule;
import com.jzqs.app.mobile.MobilePortalService;
import com.jzqs.app.settings.api.BannerImageUploadResponse;
import com.jzqs.app.settings.api.DispatchAreaCodeListResponse;
import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import com.jzqs.app.settings.api.DispatchAiRunNowResponse;
import com.jzqs.app.settings.api.DispatchAiSettingsResponse;
import com.jzqs.app.settings.api.DispatchAiWorkbenchResponse;
import com.jzqs.app.settings.api.DispatchAreaMemoryListResponse;
import com.jzqs.app.settings.api.DispatchAreaMemorySourceListResponse;
import com.jzqs.app.settings.api.DispatchAreaMemoryUpdateRequest;
import com.jzqs.app.settings.api.OperationSettingsResponse;
import com.jzqs.app.settings.service.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SettingsServiceImpl implements SettingsService {
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final long MAX_BANNER_UPLOAD_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_BANNER_IMAGE = "../../assets/hero-new.jpg";
    private static final int DEFAULT_BANNER_INTERVAL_SECONDS = 3;
    private static final String DEFAULT_DISPATCH_ANCHOR_NAME = "五环天地";
    private static final String DEFAULT_DISPATCH_ANCHOR_ADDRESS = "五环天地";
    private static final String DEFAULT_DELIVERY_SUBSCRIBE_LUNCH_TIME = "11:30";
    private static final String DEFAULT_DELIVERY_SUBSCRIBE_DINNER_TIME = "17:30";
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeAudienceModule realtimeAudienceModule;
    private final MobilePortalService mobilePortalService;
    private final DispatchService dispatchService;
    private final DispatchAiJobLogModule dispatchAiJobLogModule;
    private final Path uploadRootDir;
    private final HttpClient httpClient;

    public SettingsServiceImpl(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        RealtimeAudienceModule realtimeAudienceModule,
        MobilePortalService mobilePortalService,
        DispatchService dispatchService,
        DispatchAiJobLogModule dispatchAiJobLogModule,
        @Value("${app.upload-dir:./uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.realtimeAudienceModule = realtimeAudienceModule;
        this.mobilePortalService = mobilePortalService;
        this.dispatchService = dispatchService;
        this.dispatchAiJobLogModule = dispatchAiJobLogModule;
        this.uploadRootDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder().build();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public OperationSettingsResponse operationSettings() {
        AdminSettingsSnapshot settings = jdbcTemplate.queryForObject(
            """
                SELECT ordering_enabled,
                       holiday_notice_title,
                       holiday_notice_desc,
                       banner_images,
                       banner_interval_seconds,
                       package_expiry_reminder_days,
                       package_low_balance_threshold,
                       meal_reminder_popup_enabled,
                       delivery_subscribe_enabled,
                       delivery_subscribe_lunch_time,
                       delivery_subscribe_dinner_time,
                       popup_announcement_enabled,
                       popup_announcement_content
                FROM admin_settings
                WHERE id = 1
                """,
            (rs, rowNum) -> new AdminSettingsSnapshot(
                rs.getBoolean("ordering_enabled"),
                rs.getString("holiday_notice_title"),
                rs.getString("holiday_notice_desc"),
                rs.getString("banner_images"),
                rs.getObject("banner_interval_seconds"),
                rs.getObject("package_expiry_reminder_days"),
                rs.getObject("package_low_balance_threshold"),
                rs.getBoolean("meal_reminder_popup_enabled"),
                rs.getBoolean("delivery_subscribe_enabled"),
                rs.getObject("delivery_subscribe_lunch_time"),
                rs.getObject("delivery_subscribe_dinner_time"),
                rs.getBoolean("popup_announcement_enabled"),
                rs.getString("popup_announcement_content")
            )
        );
        boolean orderingEnabled = settings != null && settings.orderingEnabled();
        return new OperationSettingsResponse(
            orderingEnabled,
            orderingEnabled ? "通道开启中" : "暂停接单中",
            safeString(settings == null ? null : settings.holidayNoticeTitle()),
            safeString(settings == null ? null : settings.holidayNoticeDesc()),
            orderingEnabled ? "熔断：一键暂停接单 (假期店休使用)" : "恢复：重新开启接单",
            normalizeStoredBannerImages(safeString(settings == null ? null : settings.bannerImages())),
            normalizeBannerIntervalSeconds(settings == null ? null : settings.bannerIntervalSeconds()),
            normalizePositiveInt(settings == null ? null : settings.packageExpiryReminderDays(), 7),
            normalizePositiveInt(settings == null ? null : settings.packageLowBalanceThreshold(), 3),
            settings != null && settings.mealReminderPopupEnabled(),
            settings != null && settings.deliverySubscribeEnabled(),
            normalizeTimeSetting(settings == null ? null : settings.deliverySubscribeLunchTime(), DEFAULT_DELIVERY_SUBSCRIBE_LUNCH_TIME),
            normalizeTimeSetting(settings == null ? null : settings.deliverySubscribeDinnerTime(), DEFAULT_DELIVERY_SUBSCRIBE_DINNER_TIME),
            settings != null && settings.popupAnnouncementEnabled(),
            safeString(settings == null ? null : settings.popupAnnouncementContent())
        );
    }

    @Override
    public DispatchAiWorkbenchResponse dispatchAiWorkbench() {
        return new DispatchAiWorkbenchResponse(buildDispatchAiSettingsResponse(), loadDispatchAiJobLogs(20));
    }

    @Override
    @Transactional
    public DispatchAiWorkbenchResponse updateDispatchRouteWorkbenchSettings(
        boolean autoScheduleEnabled,
        String autoScheduleTime,
        String defaultStrategyMode,
        String anchorAddress,
        String updatedBy
    ) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_settings
                SET auto_schedule_enabled = ?,
                    auto_schedule_time = ?,
                    default_strategy_mode = ?,
                    anchor_name = ?,
                    anchor_address = ?,
                    updated_by = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            autoScheduleEnabled,
            normalizeTimeSetting(autoScheduleTime, "00:05"),
            safeString(defaultStrategyMode).trim().isEmpty() ? "NEAR_TO_FAR" : defaultStrategyMode.trim(),
            safeString(anchorAddress).trim(),
            safeString(anchorAddress).trim(),
            safeString(updatedBy).trim().isEmpty() ? "system" : updatedBy.trim(),
            Timestamp.valueOf(LocalDateTime.now())
        );
        publishHomeEvent("system.home.changed");
        return dispatchAiWorkbench();
    }

    @Override
    @Transactional
    public DispatchAiWorkbenchResponse updateDispatchAiSettings(
        boolean autoScheduleEnabled,
        String autoScheduleTime,
        String defaultStrategyMode,
        String anchorName,
        String anchorAddress,
        boolean aiEnabled,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String aiPromptTemplate,
        String lowBalanceThreshold,
        String updatedBy
    ) {
        DispatchAiSettingsSnapshot currentSettings = requireDispatchAiSettings();
        String nextApiKey = safeString(apiKey).trim().isEmpty() ? currentSettings.apiKey() : safeString(apiKey).trim();
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_settings
                SET auto_schedule_enabled = ?,
                    auto_schedule_time = ?,
                    default_strategy_mode = ?,
                    anchor_name = ?,
                    anchor_address = ?,
                    ai_enabled = ?,
                    api_base_url = ?,
                    api_key = ?,
                    ai_model = ?,
                    ai_prompt_template = ?,
                    low_balance_threshold = ?,
                    updated_by = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            autoScheduleEnabled,
            normalizeTimeSetting(autoScheduleTime, "00:05"),
            safeString(defaultStrategyMode).trim().isEmpty() ? "NEAR_TO_FAR" : defaultStrategyMode.trim(),
            safeString(anchorName).trim(),
            safeString(anchorAddress).trim(),
            aiEnabled,
            safeString(apiBaseUrl).trim(),
            nextApiKey,
            safeString(aiModel).trim(),
            safeString(aiPromptTemplate).trim(),
            normalizeDecimalString(lowBalanceThreshold, "20.00"),
            safeString(updatedBy).trim().isEmpty() ? "system" : updatedBy.trim(),
            Timestamp.valueOf(LocalDateTime.now())
        );
        publishHomeEvent("system.home.changed");
        return dispatchAiWorkbench();
    }

    @Override
    @Transactional
    public DispatchAiWorkbenchResponse refreshDispatchAiBalance(String updatedBy) {
        DispatchAiSettingsSnapshot settings = requireDispatchAiSettings();
        if (settings.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先配置 API Key");
        }
        long logId = insertDispatchAiJobLog(
            "BALANCE_REFRESH",
            null,
            null,
            null,
            null,
            "SUCCESS",
            null,
            "",
            "开始刷新余额",
            null,
            updatedBy
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(settings.apiBaseUrl()) + "/user/balance"))
                .header("Authorization", "Bearer " + settings.apiKey())
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek 余额查询失败，HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode balanceInfo = root.path("balance_infos").isArray() && root.path("balance_infos").size() > 0
                ? root.path("balance_infos").get(0)
                : objectMapper.createObjectNode();
            jdbcTemplate.update(
                """
                    UPDATE dispatch_ai_settings
                    SET balance_available = ?,
                        balance_currency = ?,
                        total_balance = ?,
                        granted_balance = ?,
                        topped_up_balance = ?,
                        balance_checked_at = ?,
                        updated_by = ?,
                        updated_at = ?
                    WHERE id = 1
                    """,
                root.path("is_available").asBoolean(false),
                balanceInfo.path("currency").asText("CNY"),
                balanceInfo.path("total_balance").asText(""),
                balanceInfo.path("granted_balance").asText(""),
                balanceInfo.path("topped_up_balance").asText(""),
                Timestamp.valueOf(LocalDateTime.now()),
                safeString(updatedBy).trim().isEmpty() ? "system" : updatedBy.trim(),
                Timestamp.valueOf(LocalDateTime.now())
            );
            finishDispatchAiJobLog(logId, "SUCCESS", "余额刷新成功", response.body(), null, null, "");
        } catch (Exception ex) {
            finishDispatchAiJobLog(logId, "FAILED", "余额刷新失败：" + ex.getMessage(), null, null, null, "");
            throw new IllegalStateException("刷新余额失败", ex);
        }
        return dispatchAiWorkbench();
    }

    @Override
    public DispatchAreaCodeListResponse listDispatchAreaCodes() {
        List<String> areaCodes = jdbcTemplate.query(
            """
                SELECT area_code
                FROM (
                    SELECT area_code
                    FROM dispatch_area_bindings
                    WHERE area_code IS NOT NULL AND TRIM(area_code) <> ''
                    UNION
                    SELECT area_code
                    FROM dispatch_assignments
                    WHERE area_code IS NOT NULL AND TRIM(area_code) <> ''
                    UNION
                    SELECT default_area_code AS area_code
                    FROM rider_profiles
                    WHERE default_area_code IS NOT NULL AND TRIM(default_area_code) <> ''
                ) areas
                ORDER BY area_code
                """,
            (rs, rowNum) -> rs.getString("area_code")
        );
        return new DispatchAreaCodeListResponse(areaCodes == null ? List.of() : areaCodes);
    }

    @Override
    public DispatchAreaMemoryListResponse listDispatchAreaMemories(String areaCode) {
        String normalizedAreaCode = safeString(areaCode).trim();
        if (normalizedAreaCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "areaCode is required");
        }
        List<DispatchAreaMemoryListResponse.AreaMemoryItem> items = jdbcTemplate.query(
            """
                SELECT id, memory_type, title, summary, applicable_scene, weight, status, updated_at
                FROM dispatch_area_ai_memories
                WHERE area_code = ?
                  AND status <> 'DELETED'
                ORDER BY weight DESC, updated_at DESC
                """,
            (rs, rowNum) -> new DispatchAreaMemoryListResponse.AreaMemoryItem(
                rs.getLong("id"),
                rs.getString("memory_type"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("applicable_scene"),
                rs.getInt("weight"),
                rs.getString("status"),
                formatTimestamp(rs.getTimestamp("updated_at"))
            ),
            normalizedAreaCode
        );
        return new DispatchAreaMemoryListResponse(normalizedAreaCode, items);
    }

    @Override
    public DispatchAreaMemorySourceListResponse getDispatchAreaMemorySources(long memoryId) {
        List<Map<String, Object>> memoryRows = jdbcTemplate.queryForList(
            """
                SELECT area_code, title, source_correction_ids
                FROM dispatch_area_ai_memories
                WHERE id = ?
                """,
            memoryId
        );
        if (memoryRows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "区域记忆不存在");
        }
        Map<String, Object> memoryRow = memoryRows.get(0);
        String areaCode = safeString(memoryRow.get("area_code")).trim();
        String title = safeString(memoryRow.get("title")).trim();
        List<Long> correctionIds = readLongList(memoryRow.get("source_correction_ids"));
        if (correctionIds.isEmpty()) {
            return new DispatchAreaMemorySourceListResponse(areaCode, memoryId, title, List.of());
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(correctionIds.size(), "?"));
        String sourceSql = """
            SELECT id, correction_mode, merchant_instruction, merchant_reason_summary,
                   ai_interpretation_summary, replan_status, confirmed_at
            FROM dispatch_area_ai_corrections
            WHERE id IN (%s)
            """.formatted(placeholders);
        List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(sourceSql, correctionIds.toArray());
        Map<Long, DispatchAreaMemorySourceListResponse.MemorySourceItem> itemMap = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : sourceRows) {
            long correctionId = ((Number) row.get("id")).longValue();
            itemMap.put(correctionId, new DispatchAreaMemorySourceListResponse.MemorySourceItem(
                correctionId,
                safeString(row.get("correction_mode")).trim(),
                safeString(row.get("merchant_instruction")).trim(),
                safeString(row.get("merchant_reason_summary")).trim(),
                safeString(row.get("ai_interpretation_summary")).trim(),
                safeString(row.get("replan_status")).trim(),
                formatTimestamp((Timestamp) row.get("confirmed_at"))
            ));
        }
        List<DispatchAreaMemorySourceListResponse.MemorySourceItem> items = correctionIds.stream()
            .map(itemMap::get)
            .filter(java.util.Objects::nonNull)
            .toList();
        return new DispatchAreaMemorySourceListResponse(areaCode, memoryId, title, items);
    }

    @Override
    @Transactional
    public DispatchAreaMemoryListResponse updateDispatchAreaMemory(long memoryId, DispatchAreaMemoryUpdateRequest request, String updatedBy) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT area_code
                FROM dispatch_area_ai_memories
                WHERE id = ?
                """,
            memoryId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "区域记忆不存在");
        }
        String areaCode = safeString(rows.get(0).get("area_code")).trim();
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_memories
                SET title = ?,
                    summary = ?,
                    applicable_scene = ?,
                    status = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            safeString(request.title()).trim(),
            safeString(request.summary()).trim(),
            safeString(request.applicableScene()).trim().toUpperCase(),
            safeString(request.status()).trim().toUpperCase(),
            safeString(updatedBy).trim().isEmpty() ? "system" : updatedBy.trim(),
            memoryId
        );
        return listDispatchAreaMemories(areaCode);
    }

    @Override
    @Transactional
    public DispatchAreaMemoryListResponse deleteDispatchAreaMemory(long memoryId, String updatedBy) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT area_code
                FROM dispatch_area_ai_memories
                WHERE id = ?
                """,
            memoryId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "区域记忆不存在");
        }
        String areaCode = safeString(rows.get(0).get("area_code")).trim();
        jdbcTemplate.update(
            """
                UPDATE dispatch_area_ai_memories
                SET status = 'DELETED',
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            safeString(updatedBy).trim().isEmpty() ? "system" : updatedBy.trim(),
            memoryId
        );
        return listDispatchAreaMemories(areaCode);
    }

    @Override
    @Transactional
    public DispatchAiRunNowResponse runDispatchAiNow(String serveDate, String mealPeriod, String areaCode, String operatorName) {
        DispatchAiSettingsSnapshot settings = requireDispatchAiSettings();
        List<String> mealPeriods = resolveMealPeriods(mealPeriod);
        LocalDate targetDate = parseOrDefaultDate(serveDate, LocalDate.now(APP_ZONE).plusDays(1));
        int scannedAreaCount = 0;
        int successCount = 0;
        int failedCount = 0;
        for (String targetMealPeriod : mealPeriods) {
            RunSummary summary = executeDispatchAiRun(
                targetDate,
                targetMealPeriod,
                areaCode,
                settings,
                "MANUAL_RUN",
                safeString(operatorName).trim().isEmpty() ? "system" : operatorName.trim()
            );
            scannedAreaCount += summary.scannedAreaCount();
            successCount += summary.successCount();
            failedCount += summary.failedCount();
        }
        return new DispatchAiRunNowResponse(
            targetDate.toString(),
            mealPeriods.size() == 1 ? mealPeriods.get(0) : "ALL",
            scannedAreaCount,
            successCount,
            failedCount,
            failedCount > 0 ? "已执行，部分区域失败，请查看运行日志" : "执行完成"
        );
    }

    @Override
    @Transactional
    public void runDispatchAiAutoSchedule() {
        DispatchAiSettingsSnapshot settings = requireDispatchAiSettings();
        if (!settings.autoScheduleEnabled()) {
            return;
        }
        String currentTime = LocalTime.now(APP_ZONE).withSecond(0).withNano(0).toString();
        if (!normalizeTimeSetting(currentTime, "").equals(settings.autoScheduleTime())) {
            return;
        }
        LocalDate tomorrow = LocalDate.now(APP_ZONE).plusDays(1);
        Integer existingCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM dispatch_ai_job_logs
                WHERE trigger_source = 'AUTO_SCHEDULE'
                  AND serve_date = ?
                  AND started_at >= ?
                """,
            Integer.class,
            java.sql.Date.valueOf(tomorrow),
            Timestamp.valueOf(LocalDate.now(APP_ZONE).atStartOfDay())
        );
        if (existingCount != null && existingCount > 0) {
            return;
        }
        executeDispatchAiRun(tomorrow, "LUNCH", null, settings, "AUTO_SCHEDULE", "system");
        executeDispatchAiRun(tomorrow, "DINNER", null, settings, "AUTO_SCHEDULE", "system");
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateOrderingEnabled(boolean enabled) {
        jdbcTemplate.update("UPDATE admin_settings SET ordering_enabled = ?, updated_at = ? WHERE id = 1", enabled, Timestamp.valueOf(LocalDateTime.now()));
        publishHomeEvent("system.announcement.changed");
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateHolidayNotice(String title, String desc) {
        jdbcTemplate.update("UPDATE admin_settings SET holiday_notice_title = ?, holiday_notice_desc = ?, updated_at = ? WHERE id = 1", title, desc, Timestamp.valueOf(LocalDateTime.now()));
        publishHomeEvent("system.announcement.changed");
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateBannerImages(String bannerImages) {
        return updateBannerImages(bannerImages, DEFAULT_BANNER_INTERVAL_SECONDS);
    }

    @Override
    @Transactional
    public OperationSettingsResponse updateBannerImages(String bannerImages, int bannerIntervalSeconds) {
        jdbcTemplate.update(
            "UPDATE admin_settings SET banner_images = ?, banner_interval_seconds = ?, updated_at = ? WHERE id = 1",
            normalizeStoredBannerImages(bannerImages),
            Math.max(1, bannerIntervalSeconds),
            Timestamp.valueOf(LocalDateTime.now())
        );
        publishHomeEvent("system.home.changed");
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updatePopupAnnouncement(String title, String desc, boolean enabled, String content) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDesc = desc == null ? "" : desc.trim();
        String normalizedContent = content == null ? "" : content.trim();
        if (enabled && normalizedContent.isEmpty()) {
            normalizedContent = buildPopupContent(normalizedTitle, normalizedDesc);
        }
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET holiday_notice_title = ?,
                    holiday_notice_desc = ?,
                    popup_announcement_enabled = ?,
                    popup_announcement_content = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            normalizedTitle,
            normalizedDesc,
            enabled,
            enabled ? normalizedContent : "",
            Timestamp.valueOf(LocalDateTime.now())
        );
        publishHomeEvent("system.announcement.changed");
        return operationSettings();
    }

    @Override
    @Transactional
    public OperationSettingsResponse updatePackageReminderSettings(
        int packageExpiryReminderDays,
        int packageLowBalanceThreshold,
        boolean mealReminderPopupEnabled,
        boolean deliverySubscribeEnabled,
        String deliverySubscribeLunchTime,
        String deliverySubscribeDinnerTime
    ) {
        OperationSettingsResponse previousSettings = operationSettings();
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET package_expiry_reminder_days = ?,
                    package_low_balance_threshold = ?,
                    meal_reminder_popup_enabled = ?,
                    delivery_subscribe_enabled = ?,
                    delivery_subscribe_lunch_time = ?,
                    delivery_subscribe_dinner_time = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            Math.max(1, packageExpiryReminderDays),
            Math.max(1, packageLowBalanceThreshold),
            mealReminderPopupEnabled,
            deliverySubscribeEnabled,
            normalizeTimeSetting(deliverySubscribeLunchTime, DEFAULT_DELIVERY_SUBSCRIBE_LUNCH_TIME),
            normalizeTimeSetting(deliverySubscribeDinnerTime, DEFAULT_DELIVERY_SUBSCRIBE_DINNER_TIME),
            Timestamp.valueOf(LocalDateTime.now())
        );
        if (previousSettings.deliverySubscribeEnabled() && !deliverySubscribeEnabled) {
            mobilePortalService.sendAllDeliveredPendingSubscriptions();
        }
        publishHomeEvent("system.home.changed");
        return operationSettings();
    }

    @Override
    public BannerImageUploadResponse uploadBannerImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先选择轮播图图片");
        }
        if (file.getSize() > MAX_BANNER_UPLOAD_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "轮播图图片不能超过 5MB");
        }
        String extension = resolveUploadExtension(file.getOriginalFilename(), file.getContentType());
        LocalDate today = LocalDate.now(APP_ZONE);
        String fileName = "banner-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;
        Path relativePath = Path.of("settings-banners", today.toString(), fileName);
        Path targetPath = uploadRootDir.resolve(relativePath).normalize();
        ensureWithinUploadRoot(targetPath);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存轮播图图片失败", ex);
        }
        String publicPath = toPublicUploadPath(relativePath);
        return new BannerImageUploadResponse(publicPath, publicPath, file.getSize());
    }

    @Override
    @Transactional
    public OperationSettingsResponse pauseOrderingWithNotice(String title, String desc, boolean popupEnabled, String popupContent) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDesc = desc == null ? "" : desc.trim();
        String normalizedPopupContent = popupContent == null ? "" : popupContent.trim();
        if (popupEnabled && normalizedPopupContent.isEmpty()) {
            normalizedPopupContent = buildPopupContent(normalizedTitle, normalizedDesc);
        }
        jdbcTemplate.update(
            """
                UPDATE admin_settings
                SET ordering_enabled = FALSE,
                    holiday_notice_title = ?,
                    holiday_notice_desc = ?,
                    popup_announcement_enabled = ?,
                    popup_announcement_content = ?,
                    updated_at = ?
                WHERE id = 1
                """,
            normalizedTitle,
            normalizedDesc,
            popupEnabled,
            popupEnabled ? normalizedPopupContent : "",
            Timestamp.valueOf(LocalDateTime.now())
        );
        publishHomeEvent("system.announcement.changed");
        return operationSettings();
    }

    private String buildPopupContent(String title, String desc) {
        if (title.isBlank()) {
            return desc;
        }
        if (desc.isBlank()) {
            return title;
        }
        return title + "\n" + desc;
    }

    private DispatchAiSettingsResponse buildDispatchAiSettingsResponse() {
        DispatchAiSettingsSnapshot settings = requireDispatchAiSettings();
        return new DispatchAiSettingsResponse(
            settings.autoScheduleEnabled(),
            settings.autoScheduleTime(),
            settings.defaultStrategyMode(),
            normalizeDispatchAnchorName(settings.anchorName()),
            normalizeDispatchAnchorAddress(settings.anchorAddress()),
            settings.aiEnabled(),
            settings.apiBaseUrl(),
            maskApiKey(settings.apiKey()),
            settings.aiModel(),
            settings.aiPromptTemplate(),
            settings.balanceCurrency(),
            settings.balanceAvailable(),
            settings.totalBalance(),
            settings.grantedBalance(),
            settings.toppedUpBalance(),
            formatTimestamp(settings.balanceCheckedAt()),
            settings.lowBalanceThreshold()
        );
    }

    private DispatchAiSettingsSnapshot requireDispatchAiSettings() {
        DispatchAiSettingsSnapshot settings = jdbcTemplate.queryForObject(
            """
                SELECT auto_schedule_enabled,
                       auto_schedule_time,
                       default_strategy_mode,
                       anchor_name,
                       anchor_address,
                       ai_enabled,
                       api_base_url,
                       api_key,
                       ai_model,
                       ai_prompt_template,
                       low_balance_threshold,
                       balance_available,
                       balance_currency,
                       total_balance,
                       granted_balance,
                       topped_up_balance,
                       balance_checked_at,
                       updated_by,
                       updated_at
                FROM dispatch_ai_settings
                WHERE id = 1
                """,
            (rs, rowNum) -> new DispatchAiSettingsSnapshot(
                rs.getBoolean("auto_schedule_enabled"),
                normalizeTimeSetting(rs.getString("auto_schedule_time"), "00:05"),
                safeString(rs.getString("default_strategy_mode")).trim().isEmpty() ? "NEAR_TO_FAR" : rs.getString("default_strategy_mode"),
                safeString(rs.getString("anchor_name")),
                safeString(rs.getString("anchor_address")),
                rs.getBoolean("ai_enabled"),
                safeString(rs.getString("api_base_url")),
                safeString(rs.getString("api_key")),
                safeString(rs.getString("ai_model")),
                safeString(rs.getString("ai_prompt_template")),
                safeString(rs.getString("low_balance_threshold")),
                rs.getBoolean("balance_available"),
                safeString(rs.getString("balance_currency")),
                safeString(rs.getString("total_balance")),
                safeString(rs.getString("granted_balance")),
                safeString(rs.getString("topped_up_balance")),
                rs.getTimestamp("balance_checked_at"),
                safeString(rs.getString("updated_by")),
                rs.getTimestamp("updated_at")
            )
        );
        if (settings == null) {
            throw new IllegalStateException("未找到 AI 排线配置");
        }
        return settings;
    }

    private List<DispatchAiJobLogResponse> loadDispatchAiJobLogs(int limit) {
        return jdbcTemplate.query(
            """
                SELECT id,
                       trigger_source,
                       serve_date,
                       meal_period,
                       area_code,
                       suggestion_id,
                       status,
                       suggestion_source,
                       reason_summary,
                       message,
                       metadata_json,
                       executed_by,
                       started_at,
                       finished_at
                FROM dispatch_ai_job_logs
                ORDER BY started_at DESC, id DESC
                LIMIT ?
                """,
            (rs, rowNum) -> dispatchAiJobLogModule.readLog(rs),
            limit
        );
    }

    private RunSummary executeDispatchAiRun(
        LocalDate serveDate,
        String mealPeriod,
        String areaCode,
        DispatchAiSettingsSnapshot settings,
        String triggerSource,
        String operatorName
    ) {
        List<String> areaCodes = loadDispatchAreaCodes(serveDate, mealPeriod, areaCode);
        int successCount = 0;
        int failedCount = 0;
        for (String currentAreaCode : areaCodes) {
            long logId = insertDispatchAiJobLog(
                triggerSource,
                serveDate,
                mealPeriod,
                currentAreaCode,
                null,
                "SUCCESS",
                null,
                "",
                "开始排线",
                null,
                operatorName
            );
            try {
                DispatchRouteSuggestionResponse response = dispatchService.suggestAreaRoute(
                    currentAreaCode,
                    new DispatchRouteSuggestionRequest(
                        serveDate.toString(),
                        mealPeriod,
                        settings.defaultStrategyMode(),
                        settings.anchorAddress(),
                        settings.anchorAddress(),
                        true
                    )
                );
                if (response.items() == null || response.items().isEmpty()) {
                    finishDispatchAiJobLog(
                        logId,
                        response.runStatusCode(),
                        response.runStatusDescription(),
                        buildRunMetadataJson(response),
                        response.suggestionId() > 0 ? response.suggestionId() : null,
                        response.suggestionSource(),
                        response.reasonSummary()
                    );
                    continue;
                }
                dispatchService.reorderAreaOrders(
                    currentAreaCode,
                    response.items().stream()
                        .map(this::toReorderItem)
                        .toList()
                );
                finishDispatchAiJobLog(
                    logId,
                    response.runStatusCode(),
                    response.runStatusDescription(),
                    buildRunMetadataJson(response),
                    response.suggestionId(),
                    response.suggestionSource(),
                    response.reasonSummary()
                );
                successCount++;
            } catch (Exception ex) {
                finishDispatchAiJobLog(logId, "FAILED_INTERNAL", "排线失败：" + ex.getMessage(), "{}", null, null, "");
                failedCount++;
            }
        }
        return new RunSummary(areaCodes.size(), successCount, failedCount);
    }

    private DispatchOrderReorderItemRequest toReorderItem(DispatchRouteSuggestionItemResponse item) {
        return new DispatchOrderReorderItemRequest(item.orderId(), item.suggestedSequence());
    }

    private List<String> loadDispatchAreaCodes(LocalDate serveDate, String mealPeriod, String areaCode) {
        if (areaCode != null && !areaCode.trim().isEmpty()) {
            return List.of(areaCode.trim());
        }
        List<String> areaCodes = jdbcTemplate.query(
            """
                SELECT DISTINCT COALESCE(da.area_code, rab.area_code) AS area_code
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id = mso.id
                LEFT JOIN rider_address_bindings rab ON rab.customer_id = doo.customer_id AND rab.address_id = mso.address_id
                WHERE doo.serve_date = ?
                  AND COALESCE(mso.delivery_meal_period, mso.meal_period) = ?
                  AND COALESCE(da.area_code, rab.area_code) IS NOT NULL
                  AND COALESCE(da.area_code, rab.area_code) <> ''
                ORDER BY area_code
                """,
            (rs, rowNum) -> rs.getString("area_code"),
            java.sql.Date.valueOf(serveDate),
            mealPeriod
        );
        return areaCodes == null ? List.of() : areaCodes;
    }

    private List<String> resolveMealPeriods(String mealPeriod) {
        if (mealPeriod == null || mealPeriod.trim().isEmpty()) {
            return List.of("LUNCH", "DINNER");
        }
        return List.of(mealPeriod.trim().toUpperCase());
    }

    private LocalDate parseOrDefaultDate(String serveDate, LocalDate fallback) {
        if (serveDate == null || serveDate.trim().isEmpty()) {
            return fallback;
        }
        return LocalDate.parse(serveDate.trim());
    }

    private long insertDispatchAiJobLog(
        String triggerSource,
        LocalDate serveDate,
        String mealPeriod,
        String areaCode,
        Long suggestionId,
        String status,
        String suggestionSource,
        String reasonSummary,
        String message,
        String metadataJson,
        String executedBy
    ) {
        return insertAndReturnId(
            """
                INSERT INTO dispatch_ai_job_logs (
                    trigger_source,
                    serve_date,
                    meal_period,
                    area_code,
                    suggestion_id,
                    status,
                    suggestion_source,
                    reason_summary,
                    message,
                    metadata_json,
                    executed_by,
                    started_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            triggerSource,
            serveDate == null ? null : java.sql.Date.valueOf(serveDate),
            mealPeriod,
            areaCode,
            suggestionId,
            status,
            suggestionSource,
            reasonSummary,
            message,
            metadataJson,
            executedBy,
            Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private void finishDispatchAiJobLog(
        long logId,
        String status,
        String message,
        String metadataJson,
        Long suggestionId,
        String suggestionSource,
        String reasonSummary
    ) {
        jdbcTemplate.update(
            """
                UPDATE dispatch_ai_job_logs
                SET status = ?,
                    message = ?,
                    metadata_json = ?,
                    suggestion_id = ?,
                    suggestion_source = ?,
                    reason_summary = ?,
                    finished_at = ?
                WHERE id = ?
                """,
            status,
            message,
            metadataJson,
            suggestionId,
            suggestionSource,
            reasonSummary,
            Timestamp.valueOf(LocalDateTime.now()),
            logId
        );
    }

    private String buildRunMetadataJson(DispatchRouteSuggestionResponse response) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("runStatusCode", safeString(response.runStatusCode()));
        root.put("runStatusLabel", safeString(response.runStatusLabel()));
        root.put("runStatusDescription", safeString(response.runStatusDescription()));
        root.put("reasonSummary", safeString(response.reasonSummary()));
        ArrayNode arrayNode = root.putArray("items");
        for (DispatchRouteSuggestionItemResponse item : response.items()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("orderId", item.orderId());
            node.put("suggestedSequence", item.suggestedSequence());
            node.put("aiAdjusted", item.aiAdjusted());
            node.put("ruleSequence", item.ruleSequence());
            node.put("addressLabel", safeString(item.addressLabel()));
            node.put("clusterName", safeString(item.clusterName()));
            node.put("buildingName", safeString(item.buildingName()));
            node.put("roadName", safeString(item.roadName()));
            node.put("distanceBand", safeString(item.distanceBand()));
            node.put("neighborCount", item.neighborCount());
            node.put("adjustmentReason", safeString(item.adjustmentReason()));
            arrayNode.add(node);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return "{\"items\":[]}";
        }
    }

    private String normalizeDispatchAnchorName(String anchorName) {
        String normalized = safeString(anchorName).trim();
        return normalized.isEmpty() ? DEFAULT_DISPATCH_ANCHOR_NAME : normalized;
    }

    private String normalizeDispatchAnchorAddress(String anchorAddress) {
        String normalized = safeString(anchorAddress).trim();
        if (normalized.isEmpty() || "湖北省武汉市硚口区荟聚中心".equals(normalized)) {
            return DEFAULT_DISPATCH_ANCHOR_ADDRESS;
        }
        return normalized;
    }

    private String maskApiKey(String apiKey) {
        String normalized = safeString(apiKey).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() <= 8) {
            return "****";
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String normalizeDecimalString(String value, String fallback) {
        try {
            return new BigDecimal(safeString(value).trim()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String trimTrailingSlash(String baseUrl) {
        String normalized = safeString(baseUrl).trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int normalizePositiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(safeString(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String normalizeTimeSetting(Object value, String fallback) {
        String normalized = safeString(value).trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        try {
            return LocalTime.parse(normalized).withSecond(0).withNano(0).toString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void publishHomeEvent(String eventType) {
        realtimeAudienceModule.publishSystemEvent(eventType);
    }

    private String normalizeStoredBannerImages(String rawBannerImages) {
        String normalized = rawBannerImages == null ? "" : rawBannerImages.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return defaultBannerImagesJson();
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            if (!root.isArray() || root.isEmpty()) {
                return defaultBannerImagesJson();
            }
            ArrayNode normalizedArray = objectMapper.createArrayNode();
            for (JsonNode node : root) {
                if (node == null || node.isNull()) {
                    continue;
                }
                if (node.isTextual()) {
                    String imageUrl = node.asText("").trim();
                    if (!imageUrl.isEmpty()) {
                        normalizedArray.add(createBannerNode(normalizeStoredBannerImageUrl(imageUrl), "", "", true));
                    }
                    continue;
                }
                if (!node.isObject()) {
                    continue;
                }
                String imageUrl = node.path("imageUrl").asText("").trim();
                if (imageUrl.isEmpty()) {
                    imageUrl = node.path("url").asText("").trim();
                }
                if (imageUrl.isEmpty()) {
                    continue;
                }
                boolean enabled = !node.has("enabled") || node.path("enabled").asBoolean(true);
                normalizedArray.add(createBannerNode(
                    normalizeStoredBannerImageUrl(imageUrl),
                    node.path("title").asText(""),
                    node.path("description").asText(""),
                    enabled
                ));
            }
            return normalizedArray.isEmpty() ? defaultBannerImagesJson() : objectMapper.writeValueAsString(normalizedArray);
        } catch (JsonProcessingException ex) {
            return defaultBannerImagesJson();
        }
    }

    private String normalizeStoredBannerImageUrl(String imageUrl) {
        String normalized = safeString(imageUrl).trim();
        if (normalized.isEmpty()) {
            return DEFAULT_BANNER_IMAGE;
        }
        if (!normalized.startsWith("/uploads/")) {
            return normalized;
        }
        String relative = normalized.substring("/uploads/".length()).replace('/', java.io.File.separatorChar);
        Path targetPath = uploadRootDir.resolve(relative).normalize();
        if (!targetPath.startsWith(uploadRootDir)) {
            return DEFAULT_BANNER_IMAGE;
        }
        return normalized;
    }

    private ObjectNode createBannerNode(
        String imageUrl,
        String title,
        String description,
        boolean enabled
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("imageUrl", imageUrl);
        node.put("title", title);
        node.put("description", description);
        node.put("enabled", enabled);
        return node;
    }

    private String defaultBannerImagesJson() {
        try {
            return objectMapper.writeValueAsString(
                objectMapper.createArrayNode().add(
                    createBannerNode(DEFAULT_BANNER_IMAGE, "", "", true)
                )
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("默认轮播图配置序列化失败", ex);
        }
    }

    private int normalizeBannerIntervalSeconds(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(safeString(value).trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_BANNER_INTERVAL_SECONDS;
        }
    }

    private String resolveUploadExtension(String originalFilename, String contentType) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        if (lowerContentType.contains("png")) {
            return ".png";
        }
        if (lowerContentType.contains("webp")) {
            return ".webp";
        }
        if (lowerContentType.contains("gif")) {
            return ".gif";
        }
        String normalizedName = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        if (normalizedName.endsWith(".png")) {
            return ".png";
        }
        if (normalizedName.endsWith(".webp")) {
            return ".webp";
        }
        if (normalizedName.endsWith(".gif")) {
            return ".gif";
        }
        return ".jpg";
    }

    private void ensureWithinUploadRoot(Path targetPath) {
        if (!targetPath.startsWith(uploadRootDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的图片存储路径");
        }
    }

    private String toPublicUploadPath(Path relativePath) {
        return "/uploads/" + relativePath.toString().replace('\\', '/');
    }

    private long insertAndReturnId(String sql, Object... args) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("插入记录失败，未返回主键");
        }
        return keyHolder.getKey().longValue();
    }

    private List<Long> readLongList(Object rawValue) {
        try {
            return objectMapper.readValue(safeString(rawValue), LONG_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record AdminSettingsSnapshot(
        boolean orderingEnabled,
        String holidayNoticeTitle,
        String holidayNoticeDesc,
        String bannerImages,
        Object bannerIntervalSeconds,
        Object packageExpiryReminderDays,
        Object packageLowBalanceThreshold,
        boolean mealReminderPopupEnabled,
        boolean deliverySubscribeEnabled,
        Object deliverySubscribeLunchTime,
        Object deliverySubscribeDinnerTime,
        boolean popupAnnouncementEnabled,
        String popupAnnouncementContent
    ) {
    }

    private record DispatchAiSettingsSnapshot(
        boolean autoScheduleEnabled,
        String autoScheduleTime,
        String defaultStrategyMode,
        String anchorName,
        String anchorAddress,
        boolean aiEnabled,
        String apiBaseUrl,
        String apiKey,
        String aiModel,
        String aiPromptTemplate,
        String lowBalanceThreshold,
        boolean balanceAvailable,
        String balanceCurrency,
        String totalBalance,
        String grantedBalance,
        String toppedUpBalance,
        Timestamp balanceCheckedAt,
        String updatedBy,
        Timestamp updatedAt
    ) {
    }

    private record RunSummary(
        int scannedAreaCount,
        int successCount,
        int failedCount
    ) {
    }
}
