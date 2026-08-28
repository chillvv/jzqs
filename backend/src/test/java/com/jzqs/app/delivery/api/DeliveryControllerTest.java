package com.jzqs.app.delivery.api;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.delivery.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @MockBean
    private DeliveryService deliveryService;
    @MockBean
    private com.jzqs.app.mobile.MerchantDeliveryReceiptFacade merchantDeliveryReceiptFacade;

    @Test
    void shouldAcceptDeliveryReceipt() throws Exception {
        given(merchantDeliveryReceiptFacade.submitMerchantReceipt(
            eq(2001L),
            eq("https://cos.example.com/r1.jpg"),
            eq("已放前台"),
            eq("2026-05-13T12:05:00")
        )).willReturn(new DeliveryReceiptRecordResponse(
            2001L,
            "DELIVERED",
            "SKIPPED",
            "SKIPPED",
            "https://cos.example.com/r1.jpg",
            null,
            null
        ));
        mockMvc.perform(post("/api/admin/deliveries/receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      \"mealSlotOrderId\": 2001,
                      \"receiptUrl\": \"https://cos.example.com/r1.jpg\",
                      \"receiptNote\": \"已放前台\",
                      \"deliveredAt\": \"2026-05-13T12:05:00\"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));
    }
}
