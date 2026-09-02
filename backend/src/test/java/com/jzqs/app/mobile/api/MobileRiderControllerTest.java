package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.mobile.MobileAuthService;
import com.jzqs.app.mobile.MobilePortalService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileRiderController.class)
class MobileRiderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private MobileAuthService mobileAuthService;

    @MockBean
    private MobilePortalService mobilePortalService;

    @Test
    void shouldReturnRiderTasksFromAuthenticatedRiderContext() throws Exception {
        given(mobilePortalService.riderTasks(9L)).willReturn(PageResponse.of(List.of(
            new RiderTaskItemResponse(
                2L,
                3L,
                "王总",
                "13700000003",
                "财富中心写字楼1201",
                "LUNCH",
                "LUNCH",
                "LUNCH",
                "香煎鸡胸肉套餐",
                "微辣",
                "DISPATCHING",
                "PENDING",
                ""
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/rider/tasks").requestAttr("riderId", 9L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].customerName").value("王总"))
            .andExpect(jsonPath("$.data.items[0].deliveryStatus").value("DISPATCHING"));
    }

    @Test
    void shouldReturnOkWhenNoRiderContextProvided() throws Exception {
        given(mobilePortalService.riderTasks(org.mockito.ArgumentMatchers.isNull()))
            .willReturn(PageResponse.of(java.util.List.of(), 1, 20, 0));
        mockMvc.perform(get("/api/mobile/rider/tasks"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturnRiderSummary() throws Exception {
        given(mobilePortalService.riderSummary(9L, "2026-05-12")).willReturn(
            new RiderBatchSummaryResponse(
                "骑手小李",
                8,
                3,
                5,
                new RiderBatchSummaryResponse.BatchCardResponse(1L, "LUNCH", "IN_PROGRESS", 5, 2, 3, 3, "张先生", "王先生"),
                new RiderBatchSummaryResponse.BatchCardResponse(2L, "DINNER", "READY", 3, 1, 2, 1, "李女士", "赵女士")
            )
        );

        mockMvc.perform(get("/api/mobile/rider/summary")
                .requestAttr("riderId", 9L)
                .param("serveDate", "2026-05-12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderName").value("骑手小李"))
            .andExpect(jsonPath("$.data.totalCount").value(8))
            .andExpect(jsonPath("$.data.lunchBatch.currentCustomerName").value("张先生"));
    }

    @Test
    void shouldReturnRiderQueue() throws Exception {
        given(mobilePortalService.riderQueue(9L, "2026-05-12")).willReturn(PageResponse.of(List.of(
            new RiderQueueItemResponse(
                9L,
                1L,
                3L,
                1L,
                1,
                "王总",
                "13700000003",
                "财富中心写字楼1201",
                "LUNCH",
                "LUNCH",
                "LUNCH",
                "香煎鸡胸肉套餐",
                1,
                "微辣",
                "",
                false,
                List.<String>of(),
                "",
                false,
                "",
                "CURRENT",
                "PENDING",
                "",
                "",
                "",
                null,
                null
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/rider/queue")
                .requestAttr("riderId", 9L)
                .param("serveDate", "2026-05-12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].customerName").value("王总"))
            .andExpect(jsonPath("$.data.items[0].itemStatus").value("CURRENT"));
    }

    @Test
    void shouldSubmitRiderReceipt() throws Exception {
        given(mobilePortalService.submitRiderReceipt(
            eq(3L),
            eq(9L),
            eq("receipt-3.jpg"),
            eq("已放前台"),
            eq("2026-05-12T12:30:00")
        )).willReturn(new DeliveryReceiptRecordResponse(3L, "DELIVERED", "CONSUMED", "SKIPPED", "receipt-3.jpg", null, null));

        mockMvc.perform(post("/api/mobile/rider/tasks/3/receipt")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receiptFileKey": "receipt-3.jpg",
                      "receiptNote": "已放前台",
                      "deliveredAt": "2026-05-12T12:30:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(3L))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));
    }

    @Test
    void shouldUpdateRiderReceipt() throws Exception {
        given(mobilePortalService.updateRiderReceipt(
            eq(3L),
            eq(9L),
            eq("receipt-3-v2.jpg"),
            eq("已更新"),
            eq("2026-05-12T12:35:00")
        )).willReturn(new DeliveryReceiptRecordResponse(
            3L,
            "DELIVERED",
            "UNCHANGED",
            "SKIPPED",
            "receipt-3-v2.jpg",
            "2026-05-12T12:35:00",
            "2026-05-14T12:35:00"
        ));

        mockMvc.perform(post("/api/mobile/rider/tasks/3/receipt/update")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receiptFileKey": "receipt-3-v2.jpg",
                      "receiptNote": "已更新",
                      "deliveredAt": "2026-05-12T12:35:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(3L))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.data.receiptUrl").value("receipt-3-v2.jpg"));
    }

    @Test
    void shouldUploadReceiptImageToLocalStorage() throws Exception {
        given(mobilePortalService.uploadRiderReceipt(eq(9L), any()))
            .willReturn(new RiderDeliveryUploadResponse(
                "/uploads/rider-receipts/2026-06-06/rider-1.jpg",
                "/uploads/rider-receipts/2026-06-06/rider-1.jpg",
                128L
            ));

        mockMvc.perform(multipart("/api/mobile/rider/uploads/receipt")
                .file(new MockMultipartFile("file", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image".getBytes()))
                .requestAttr("riderId", 9L)
                )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileKey").value("/uploads/rider-receipts/2026-06-06/rider-1.jpg"))
            .andExpect(jsonPath("$.data.previewUrl").value("/uploads/rider-receipts/2026-06-06/rider-1.jpg"))
            .andExpect(jsonPath("$.data.size").value(128));
    }

    @Test
    void shouldDeferQueueItem() throws Exception {
        given(mobilePortalService.deferRiderQueueItem(9L, 9L))
            .willReturn(new RiderQueueItemActionResponse(9L, "DEFERRED", "DEFERRED"));

        mockMvc.perform(post("/api/mobile/rider/queue/items/9/defer").requestAttr("riderId", 9L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchItemId").value(9L))
            .andExpect(jsonPath("$.data.itemStatus").value("DEFERRED"));
    }

    @Test
    void shouldResumeQueueItem() throws Exception {
        given(mobilePortalService.resumeRiderQueueItem(9L, 9L))
            .willReturn(new RiderQueueItemActionResponse(9L, "PENDING", "RESUMED"));

        mockMvc.perform(post("/api/mobile/rider/queue/items/9/resume").requestAttr("riderId", 9L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchItemId").value(9L))
            .andExpect(jsonPath("$.data.itemStatus").value("PENDING"));
    }

    @Test
    void shouldReportDeliveryException() throws Exception {
        given(mobilePortalService.reportDeliveryException(
            eq(3L),
            eq(9L),
            eq("PHONE_OFF"),
            eq("联系不上"),
            eq(List.of("/uploads/e1.jpg"))
        )).willReturn(new RiderDeliveryExceptionReportResponse(7L, "REPORTED", "异常已上报，请等待处理"));

        mockMvc.perform(post("/api/mobile/rider/tasks/3/report-exception")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "exceptionType": "PHONE_OFF",
                      "exceptionNote": "联系不上",
                      "exceptionImages": ["/uploads/e1.jpg"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptionId").value(7L))
            .andExpect(jsonPath("$.data.status").value("REPORTED"))
            .andExpect(jsonPath("$.data.message").value("异常已上报，请等待处理"));
    }
}
