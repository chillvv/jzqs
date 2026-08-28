package com.jzqs.app.aftersale.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AftersaleController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class
})
class AftersaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @MockBean
    private AftersaleService aftersaleService;

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
    void shouldListAdminAftersales() throws Exception {
        given(aftersaleService.listCases("PENDING", "REFUND", "2026-05-27", null, null, null))
            .willReturn(List.of(new AdminAftersaleListItemResponse(
                5L, 8L, 1L, "张先生", "13800000001", "2026-05-27", "LUNCH",
                1, "PENDING_DISPATCH", "REFUND", "PENDING", "USER_APPLY",
                "NORMAL", "USER_TEMP_CHANGE", "临时有事", "",
                1, 0, 1, 0, 0,
                "REFUND_TO_WALLET", true, "",
                "2026-05-26 20:30:00", null
            )));

        mockMvc.perform(get("/api/admin/aftersales")
                .param("status", "PENDING")
                .param("type", "REFUND")
                .param("startDate", "2026-05-27"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].type").value("REFUND"))
            .andExpect(jsonPath("$.data[0].refundBlocking").value(true));
    }

    @Test
    void shouldResolveAftersaleCase() throws Exception {
        given(aftersaleService.resolveCase(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.any(AdminAftersaleResolveRequest.class)))
            .willReturn(new AdminAftersaleResolveResponse(12L, "COMPLETED", "REFUND_TO_WALLET"));

        mockMvc.perform(post("/api/admin/aftersales/12/resolve")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "resolutionAction": "REFUND_TO_WALLET",
                      "refundBlocking": false,
                      "walletDelta": 1,
                      "settledLossMeals": 0,
                      "giftZeroMealCount": 0,
                      "giftVeggieJuiceCount": 0,
                      "adminRemark": "同意退款，退回餐次"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.caseId").value(12))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.resolutionAction").value("REFUND_TO_WALLET"));

        then(aftersaleService).should().resolveCase(12L, new AdminAftersaleResolveRequest(
            "REFUND_TO_WALLET",
            false,
            1,
            0,
            0,
            0,
            "同意退款，退回餐次",
            "运营A"
        ));
    }

    @Test
    void shouldCreateAftersaleWithServerSideOperator() throws Exception {
        given(aftersaleService.createCase(org.mockito.ArgumentMatchers.any(AdminAftersaleCreateRequest.class)))
            .willReturn(new AdminAftersaleCreateResponse(18L, "PENDING"));

        mockMvc.perform(post("/api/admin/aftersales")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "orderId": 8,
                      "type": "REFUND",
                      "reasonCode": "USER_APPLY",
                      "reasonText": "用户申请退款",
                      "remark": "尽快处理"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.afterSaleId").value(18L))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        then(aftersaleService).should().createCase(new AdminAftersaleCreateRequest(
            8L,
            "REFUND",
            "USER_APPLY",
            "用户申请退款",
            null,
            0,
            null,
            "尽快处理",
            "运营A"
        ));
    }
}
