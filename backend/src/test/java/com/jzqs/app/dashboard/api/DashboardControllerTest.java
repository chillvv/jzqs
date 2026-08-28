package com.jzqs.app.dashboard.api;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.dashboard.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @MockBean
    private DashboardService dashboardService;

    @Test
    void shouldReturnDashboardOverview() throws Exception {
        given(dashboardService.overview()).willReturn(new DashboardOverviewResponse(
            98,
            76,
            43,
            33,
            6,
            13,
            4,
            3,
            118,
            new java.math.BigDecimal("7"),
            12,
            13,
            8,
            19,
            118,
            4,
            11,
            5,
            2,
            19,
            3,
            1,
            0,
            java.util.List.of(new DashboardOverviewResponse.OrderTrendPoint("05/11", 132, 76, 56)),
            java.util.List.of(new DashboardOverviewResponse.GrowthTrendPoint("05/11", 6, 13))
        ));
        mockMvc.perform(get("/api/admin/dashboard/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deliveredToday").value(98))
            .andExpect(jsonPath("$.data.tomorrowLunchCount").value(43))
            .andExpect(jsonPath("$.data.rechargeCustomersToday").value(13))
            .andExpect(jsonPath("$.data.totalOrdersToday").value(118))
            .andExpect(jsonPath("$.data.lowBalanceCustomers").value(19))
            .andExpect(jsonPath("$.data.orderTrend[0].total").value(132))
            .andExpect(jsonPath("$.data.growthTrend[0].recharges").value(13));
    }
}
