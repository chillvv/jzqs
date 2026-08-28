package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceReplaceResponse;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.MobileAuthService;
import com.jzqs.app.mobile.MobilePortalService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RiderController.class)
class RiderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private MobileAuthService mobileAuthService;

    @MockBean
    private MobilePortalService mobilePortalService;

    @Test
    void shouldSubmitRiderReceiptThroughService() throws Exception {
        given(mobilePortalService.submitRiderReceipt(
            eq(9L),
            eq(9L),
            eq("receipt-9.jpg"),
            eq("已放前台"),
            eq("2026-05-18T12:10:00")
        )).willReturn(new DeliveryReceiptRecordResponse(9L, "DELIVERED", "CONSUMED", "SKIPPED", "receipt-9.jpg", null, null));

        mockMvc.perform(post("/api/rider/orders/9/complete")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receiptFileKey": "receipt-9.jpg",
                      "receiptNote": "已放前台",
                      "deliveredAt": "2026-05-18T12:10:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(9L))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));

        verify(mobilePortalService).submitRiderReceipt(9L, 9L, "receipt-9.jpg", "已放前台", "2026-05-18T12:10:00");
    }

    @Test
    void shouldRevertOrderThroughService() throws Exception {
        given(mobilePortalService.revertOrderStatus(9L, 9L))
            .willReturn(new RiderOrderStatusRevertResponse(9L, "PENDING"));

        mockMvc.perform(post("/api/rider/orders/9/revert").requestAttr("riderId", 9L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(9L))
            .andExpect(jsonPath("$.data.newStatus").value("PENDING"));

        verify(mobilePortalService).revertOrderStatus(9L, 9L);
    }

    @Test
    void shouldReorderOrdersThroughService() throws Exception {
        given(mobilePortalService.reorderRiderQueue(9L, List.of(12L, 11L)))
            .willReturn(new RiderQueueReorderResponse(2, "REORDERED"));

        mockMvc.perform(post("/api/rider/orders/reorder")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "batchItemIds": [12, 11]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.status").value("REORDERED"));

        verify(mobilePortalService).reorderRiderQueue(9L, List.of(12L, 11L));
    }

    @Test
    void shouldSaveBatchAddressReferenceImageThroughService() throws Exception {
        given(mobilePortalService.saveBatchAddressReferenceImage(
            eq(9L),
            eq(new RiderBatchAddressReferenceRequest(List.of(12L, 11L), "reference-1.jpg"))
        )).willReturn(new RiderAddressReferenceBatchSaveResponse(2, List.of(12L, 11L)));

        mockMvc.perform(post("/api/rider/address-reference/batch")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "addressIds": [12, 11],
                      "referenceImageUrl": "reference-1.jpg"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.updatedCount").value(2))
            .andExpect(jsonPath("$.data.addressIds[0]").value(12L))
            .andExpect(jsonPath("$.data.addressIds[1]").value(11L));
    }

    @Test
    void shouldReplaceAddressReferenceImageThroughService() throws Exception {
        given(mobilePortalService.replaceAddressReferenceImage(9L, 12L, "reference-2.jpg"))
            .willReturn(new RiderAddressReferenceReplaceResponse(12L, "/uploads/reference-2.jpg", true));

        mockMvc.perform(post("/api/rider/address-reference/12")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "referenceImageUrl": "reference-2.jpg"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.addressId").value(12L))
            .andExpect(jsonPath("$.data.referenceImageUrl").value("/uploads/reference-2.jpg"))
            .andExpect(jsonPath("$.data.updated").value(true));
    }

    @Test
    void shouldUpdateReceiptThroughService() throws Exception {
        given(mobilePortalService.updateRiderReceipt(
            eq(9L),
            eq(9L),
            eq("receipt-9-v2.jpg"),
            eq("已更新"),
            eq("2026-05-18T12:20:00")
        )).willReturn(new DeliveryReceiptRecordResponse(
            9L,
            "DELIVERED",
            "UNCHANGED",
            "SKIPPED",
            "receipt-9-v2.jpg",
            "2026-05-18T12:20:00",
            "2026-05-20T12:20:00"
        ));

        mockMvc.perform(put("/api/rider/orders/9/receipt")
                .requestAttr("riderId", 9L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receiptFileKey": "receipt-9-v2.jpg",
                      "receiptNote": "已更新",
                      "deliveredAt": "2026-05-18T12:20:00"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(9L))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.data.receiptUrl").value("receipt-9-v2.jpg"));

        verify(mobilePortalService).updateRiderReceipt(9L, 9L, "receipt-9-v2.jpg", "已更新", "2026-05-18T12:20:00");
    }

    @Test
    void shouldDeleteReceiptImageThroughService() throws Exception {
        given(mobilePortalService.deleteRiderReceiptImage(9L, 9L))
            .willReturn(new DeliveryReceiptDeleteResponse(9L, "DELIVERED", "", true));

        mockMvc.perform(delete("/api/rider/orders/9/receipt-image").requestAttr("riderId", 9L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(9L))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.data.deleted").value(true));

        verify(mobilePortalService).deleteRiderReceiptImage(9L, 9L);
    }

    @Test
    void shouldReportExceptionThroughService() throws Exception {
        given(mobilePortalService.reportDeliveryException(
            eq(9L),
            eq(9L),
            eq("PHONE_OFF"),
            eq("联系不上"),
            eq(List.of("/uploads/e1.jpg"))
        )).willReturn(new RiderDeliveryExceptionReportResponse(7L, "REPORTED", "异常已上报，请等待处理"));

        mockMvc.perform(post("/api/rider/orders/9/report-exception")
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

        verify(mobilePortalService).reportDeliveryException(9L, 9L, "PHONE_OFF", "联系不上", List.of("/uploads/e1.jpg"));
    }
}
