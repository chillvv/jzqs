package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.mobile.MobilePortalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileRiderController.class)
class MobileRiderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MobilePortalService mobilePortalService;

    @Test
    void shouldReturnRiderTasks() throws Exception {
        given(mobilePortalService.riderTasks("骑手小李")).willReturn(PageResponse.of(List.of(
            new RiderTaskItemResponse(
                2L,
                3L,
                "王总",
                "13700000003",
                "财富中心写字楼1201",
                "LUNCH",
                "香煎鸡胸肉套餐",
                "微辣",
                "DISPATCHING",
                "PENDING",
                ""
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/rider/tasks").param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].customerName").value("王总"))
            .andExpect(jsonPath("$.data.items[0].deliveryStatus").value("DISPATCHING"));
    }

    @Test
    void shouldStillRequireExplicitRiderNameEvenWithBearerToken() throws Exception {
        given(mobilePortalService.riderTasks("骑手小李")).willReturn(PageResponse.of(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/mobile/rider/tasks")
                .header("Authorization", "Bearer token-rider")
                .param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void shouldRejectRiderTasksWithoutRiderNameEvenIfBearerTokenExists() throws Exception {
        mockMvc.perform(get("/api/mobile/rider/tasks")
                .header("Authorization", "Bearer token-rider"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnRiderSummary() throws Exception {
        given(mobilePortalService.riderSummary("骑手小李")).willReturn(
            new RiderBatchSummaryResponse(
                "骑手小李",
                8,
                3,
                5,
                new RiderBatchSummaryResponse.BatchCardResponse(1L, "LUNCH", "IN_PROGRESS", 5, 2, 3, 3, "张先生", "王先生"),
                new RiderBatchSummaryResponse.BatchCardResponse(2L, "DINNER", "READY", 3, 1, 2, 1, "李女士", "赵女士")
            )
        );

        mockMvc.perform(get("/api/mobile/rider/summary").param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderName").value("骑手小李"))
            .andExpect(jsonPath("$.data.totalCount").value(8))
            .andExpect(jsonPath("$.data.lunchBatch.currentCustomerName").value("张先生"));
    }

    @Test
    void shouldReturnRiderQueue() throws Exception {
        given(mobilePortalService.riderQueue("骑手小李")).willReturn(PageResponse.of(List.of(
            new RiderQueueItemResponse(
                9L,
                1L,
                3L,
                1,
                "王总",
                "13700000003",
                "财富中心写字楼1201",
                "LUNCH",
                "香煎鸡胸肉套餐",
                1,
                "微辣",
                "CURRENT",
                "PENDING",
                "",
                ""
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/rider/queue").param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].customerName").value("王总"))
            .andExpect(jsonPath("$.data.items[0].itemStatus").value("CURRENT"));
    }

    @Test
    void shouldSubmitRiderReceipt() throws Exception {
        given(mobilePortalService.submitRiderReceipt(
            eq(3L),
            eq("骑手小李"),
            eq("receipt-3.jpg"),
            eq("已放前台"),
            eq("2026-05-12T12:30:00")
        )).willReturn(Map.of("mealSlotOrderId", 3L, "orderStatus", "DELIVERED"));

        mockMvc.perform(post("/api/mobile/rider/tasks/3/receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小李",
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
    void shouldDeferQueueItem() throws Exception {
        given(mobilePortalService.deferRiderQueueItem("骑手小李", 9L))
            .willReturn(Map.of("batchItemId", 9L, "itemStatus", "DEFERRED"));

        mockMvc.perform(post("/api/mobile/rider/queue/items/9/defer").param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchItemId").value(9L))
            .andExpect(jsonPath("$.data.itemStatus").value("DEFERRED"));
    }

    @Test
    void shouldResumeQueueItem() throws Exception {
        given(mobilePortalService.resumeRiderQueueItem("骑手小李", 9L))
            .willReturn(Map.of("batchItemId", 9L, "itemStatus", "PENDING"));

        mockMvc.perform(post("/api/mobile/rider/queue/items/9/resume").param("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batchItemId").value(9L))
            .andExpect(jsonPath("$.data.itemStatus").value("PENDING"));
    }
}
