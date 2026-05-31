package com.jzqs.app.maintenance;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({MaintenanceController.class, ReceiptCleanupController.class})
class MaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataCleanupService dataCleanupService;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldTriggerCleanupAndReturnSuccessSummary() throws Exception {
        doNothing().when(dataCleanupService).manualCleanup();

        mockMvc.perform(post("/api/admin/maintenance/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.message").value("数据清理执行成功"));
    }

    @Test
    void shouldReturnMaintenanceOverview() throws Exception {
        given(dataCleanupService.fetchOverview()).willReturn(new MaintenanceOverviewResponse(
            new MaintenanceLogItemResponse(10L, "MANUAL_DATA_CLEANUP", "ADMIN", "SUCCESS", "清理 30 天前订单与 14 天前日志", null, null, 0L, 28, 20, 0, "手动清理完成", null),
            new MaintenanceLogItemResponse(11L, "AUTO_DATA_CLEANUP", "SCHEDULED", "SUCCESS", "凌晨 3 点自动清理", null, null, 0L, 120, 115, 0, "自动清理完成", null),
            new MaintenanceLogItemResponse(12L, "CLOUD_RECEIPT_CLEANUP", "WECHAT_CLOUDFUNCTION", "PARTIAL_SUCCESS", "删除今天 00:00 前的回执图片", null, null, 0L, 40, 36, 4, "cleanupReceipts 执行完成", "4 个文件删除失败"),
            new MaintenanceLogItemResponse(13L, "CLOUD_STORAGE_SWEEP", "WECHAT_CLOUDFUNCTION", "SUCCESS", "删除今天 00:00 前上传的云存储图片", null, null, 0L, 25, 25, 0, "cleanStorage 执行完成", null)
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
        given(dataCleanupService.fetchRecentLogs(20)).willReturn(List.of(
            new MaintenanceLogItemResponse(14L, "MANUAL_DATA_CLEANUP", "ADMIN", "SUCCESS", "手动清理 30 天前订单", null, null, 1200L, 30, 28, 0, "手动清理完成", null)
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
                null
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
                      "message": "cleanStorage 执行完成"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.jobType").value("CLOUD_STORAGE_SWEEP"))
            .andExpect(jsonPath("$.data.deletedCount").value(12));
    }
}
