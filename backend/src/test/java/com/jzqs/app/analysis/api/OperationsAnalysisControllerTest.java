package com.jzqs.app.analysis.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.analysis.service.OperationsAnalysisService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationsAnalysisController.class)
class OperationsAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private OperationsAnalysisService operationsAnalysisService;

    @Test
    void shouldCreateCostEntryWithTypedRequestAndResponse() throws Exception {
        given(operationsAnalysisService.createCostEntry(
            new CreateCostEntryRequest(LocalDate.parse("2026-06-26"), "食材", new BigDecimal("88.50"), "午餐补货", "后台客服")
        )).willReturn(new CreateCostEntryResponse(9L, "CREATED"));

        mockMvc.perform(post("/api/admin/analysis/cost-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "costDate": "2026-06-26",
                      "costCategory": "食材",
                      "amount": "88.50",
                      "remark": "午餐补货",
                      "recordedBy": "后台客服"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(9L))
            .andExpect(jsonPath("$.data.status").value("CREATED"));
    }
}
