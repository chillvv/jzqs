package com.jzqs.app.aftersale.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.aftersale.service.AftersaleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AftersaleController.class)
class AftersaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AftersaleService aftersaleService;

    @Test
    void shouldListAdminAftersales() throws Exception {
        given(aftersaleService.listCases("PENDING", "REFUND", "2026-05-27"))
            .willReturn(List.of(new AdminAftersaleListItemResponse(
                5L, 8L, 1L, "张先生", "13800000001", "2026-05-27", "LUNCH",
                "PENDING_DISPATCH", "REFUND", "PENDING", "USER_APPLY",
                "USER_TEMP_CHANGE", "临时有事", true, "", "2026-05-26 20:30:00", null
            )));

        mockMvc.perform(get("/api/admin/aftersales")
                .param("status", "PENDING")
                .param("type", "REFUND")
                .param("serveDate", "2026-05-27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].type").value("REFUND"))
            .andExpect(jsonPath("$.data[0].refundBlocking").value(true));
    }

    @Test
    void shouldResolveAftersaleCase() throws Exception {
        given(aftersaleService.resolveCase(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.any(AdminAftersaleResolveRequest.class)))
            .willReturn(java.util.Map.of(
                "caseId", 12L,
                "status", "COMPLETED",
                "resolutionAction", "REFUND_TO_WALLET"
            ));

        mockMvc.perform(post("/api/admin/aftersales/12/resolve")
                .contentType("application/json")
                .content("""
                    {
                      "resolutionAction": "REFUND_TO_WALLET",
                      "refundBlocking": false,
                      "walletDelta": 1,
                      "adminRemark": "同意退款，退回餐次",
                      "operatorName": "后台客服"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.caseId").value(12))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.resolutionAction").value("REFUND_TO_WALLET"));
    }
}
