package com.jzqs.app.maintenance;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({MaintenanceController.class, ReceiptCleanupController.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class MaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @Autowired
    private TestIdempotencyStore testIdempotencyStore;

    @MockBean
    private DataCleanupService dataCleanupService;

    @MockBean
    private ReceiptCleanupService receiptCleanupService;

    @AfterEach
    void tearDown() {
        inMemoryRateLimitStore.clear();
    }

    private RequestPostProcessor admin() {
        return request -> {
            request.setAttribute("userId", 7L);
            request.setAttribute("userType", "admin");
            request.setAttribute("adminDisplayName", "运营A");
            return request;
        };
    }

    @Test
    void shouldTriggerCleanupAndReturnSuccessSummary() throws Exception {
        given(dataCleanupService.manualCleanup()).willReturn("全部清理任务已执行");

        mockMvc.perform(post("/api/admin/maintenance/cleanup").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.message").value("全部清理任务已执行"));
    }

    @Test
    void shouldReturnMaintenanceOverview() throws Exception {
        given(dataCleanupService.fetchOverview()).willReturn(new MaintenanceOverviewResponse(
            new MaintenanceLogItemResponse(10L, "MANUAL_DATA_CLEANUP", null, "ADMIN", "SUCCESS", "清理 30 天前订单与 14 天前日志", null, null, 0L, 28, 20, 0, "手动清理完成", null, List.of()),
            new MaintenanceLogItemResponse(11L, "AUTO_DATA_CLEANUP", null, "SCHEDULED", "SUCCESS", "凌晨 3 点自动清理", null, null, 0L, 120, 115, 0, "自动清理完成", null, List.of()),
            new MaintenanceLogItemResponse(12L, "CLOUD_RECEIPT_CLEANUP", null, "WECHAT_CLOUDFUNCTION", "PARTIAL_SUCCESS", "删除今天 00:00 前的回执图片", null, null, 0L, 40, 36, 4, "cleanupReceipts 执行完成", "4 个文件删除失败", List.of()),
            new MaintenanceLogItemResponse(13L, "CLOUD_STORAGE_SWEEP", null, "WECHAT_CLOUDFUNCTION", "SUCCESS", "删除今天 00:00 前上传的云存储图片", null, null, 0L, 25, 25, 0, "cleanStorage 执行完成", null, List.of()),
            List.of(),
            "每日 03:00"
        ));

        mockMvc.perform(get("/api/admin/maintenance/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.latestManual.jobType").value("MANUAL_DATA_CLEANUP"))
            .andExpect(jsonPath("$.data.latestAuto.jobType").value("AUTO_DATA_CLEANUP"))
            .andExpect(jsonPath("$.data.latestCloudReceipt.jobType").value("CLOUD_RECEIPT_CLEANUP"))
            .andExpect(jsonPath("$.data.latestCloudStorage.jobType").value("CLOUD_STORAGE_SWEEP"));
    }

    @Test
    void shouldReturnRecentMaintenanceLogs() throws Exception {
        given(dataCleanupService.fetchRecentLogs(DataCleanupService.MAINTENANCE_LOG_KEEP_COUNT)).willReturn(List.of(
            new MaintenanceLogItemResponse(14L, "MANUAL_DATA_CLEANUP", null, "ADMIN", "SUCCESS", "手动清理 30 天前订单", null, null, 1200L, 30, 28, 0, "手动清理完成", null, List.of())
        ));

        mockMvc.perform(get("/api/admin/maintenance/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].jobType").value("MANUAL_DATA_CLEANUP"))
            .andExpect(jsonPath("$.data[0].deletedCount").value(28));
    }

    @Test
    void shouldAcceptCloudMaintenanceLogReport() throws Exception {
        given(dataCleanupService.recordCloudJob(org.mockito.ArgumentMatchers.any(CloudMaintenanceJobReportRequest.class)))
            .willReturn(new MaintenanceLogItemResponse(
                15L,
                "CLOUD_STORAGE_SWEEP",
                null,
                "WECHAT_CLOUDFUNCTION",
                "SUCCESS",
                "删除今天 00:00 前上传的云存储图片",
                null,
                null,
                1800L,
                12,
                12,
                0,
                "cleanStorage 执行完成",
                null,
                List.of()
            ));

        mockMvc.perform(post("/api/internal/maintenance/cloud-job-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "jobType": "CLOUD_STORAGE_SWEEP",
                      "status": "SUCCESS",
                      "timeRangeLabel": "删除今天 00:00 前上传的云存储图片",
                      "scannedCount": 12,
                      "deletedCount": 12,
                      "failedCount": 0,
                      "message": "cleanStorage 执行完成",
                      "metadata": {
                        "cutoff": "2026-06-26T00:00:00.000Z",
                        "requested": 12,
                        "warnings": ["warn-a"]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.jobType").value("CLOUD_STORAGE_SWEEP"))
            .andExpect(jsonPath("$.data.deletedCount").value(12));

        ArgumentCaptor<CloudMaintenanceJobReportRequest> captor = ArgumentCaptor.forClass(CloudMaintenanceJobReportRequest.class);
        then(dataCleanupService).should().recordCloudJob(captor.capture());
        CloudMaintenanceJobReportRequest request = captor.getValue();
        CloudMaintenanceJobMetadataRequest metadata = request.metadata();
        org.junit.jupiter.api.Assertions.assertEquals("CLOUD_STORAGE_SWEEP", request.jobType());
        org.junit.jupiter.api.Assertions.assertEquals("2026-06-26T00:00:00.000Z", metadata.cutoff());
        org.junit.jupiter.api.Assertions.assertEquals(12, metadata.requested());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("warn-a"), metadata.warnings());
    }

    @Test
    void shouldReturnExpiredReceiptFileIds() throws Exception {
        given(receiptCleanupService.getExpiredFileIds()).willReturn(
            new ExpiredReceiptFilesResponse(List.of("cloud://bucket/receipt-a.jpg", "cloud://bucket/receipt-b.jpg"), 2, "2026-06-26T00:00:00")
        );

        mockMvc.perform(get("/api/internal/receipts/expired-file-ids"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.fileIds[0]").value("cloud://bucket/receipt-a.jpg"))
            .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void shouldMarkReceiptFilesAsCloudDeleted() throws Exception {
        given(receiptCleanupService.markCloudDeleted(new MarkCloudDeletedRequest(List.of("cloud://bucket/receipt-a.jpg", "cloud://bucket/receipt-b.jpg"))))
            .willReturn(new MarkCloudDeletedResponse(2, 2));

        mockMvc.perform(post("/api/internal/receipts/mark-cloud-deleted")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fileIds": [
                        "cloud://bucket/receipt-a.jpg",
                        "cloud://bucket/receipt-b.jpg"
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.updated").value(2))
            .andExpect(jsonPath("$.data.requested").value(2));
    }
}
