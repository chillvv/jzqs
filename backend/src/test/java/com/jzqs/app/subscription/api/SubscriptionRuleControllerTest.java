package com.jzqs.app.subscription.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.subscription.service.SubscriptionRuleService;
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

@WebMvcTest(SubscriptionRuleController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class SubscriptionRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @Autowired
    private TestIdempotencyStore testIdempotencyStore;

    @MockBean
    private SubscriptionRuleService subscriptionRuleService;

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
    void shouldDeleteRuleWithTypedResponse() throws Exception {
        given(subscriptionRuleService.deleteRule(7L))
            .willReturn(new SubscriptionRuleDeleteResponse(7L, true));

        mockMvc.perform(delete("/api/admin/subscription-rules/7").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(7L))
            .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void shouldTogglePauseWithTypedResponse() throws Exception {
        given(subscriptionRuleService.togglePause(7L))
            .willReturn(new SubscriptionRuleTogglePauseResponse(7L, true));

        mockMvc.perform(post("/api/admin/subscription-rules/7/toggle").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(7L))
            .andExpect(jsonPath("$.data.paused").value(true));
    }
}
