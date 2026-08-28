package com.jzqs.app.packageplan.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.packageplan.service.PackageGrantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PackageGrantController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class PackageGrantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @Autowired
    private TestIdempotencyStore testIdempotencyStore;

    @MockBean
    private PackageGrantService packageGrantService;

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
    void shouldGrantPackageWithTypedResponse() throws Exception {
        given(packageGrantService.grantPackage(9921L, "MONTH_33", 33, "运营A", 7L))
            .willReturn(new GrantPackageResponse(9921L, "MONTH_33", 33));

        mockMvc.perform(post("/api/admin/package-grants")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": 9921,
                      "packageCode": "MONTH_33",
                      "totalMeals": 33
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(9921L))
            .andExpect(jsonPath("$.data.packageCode").value("MONTH_33"))
            .andExpect(jsonPath("$.data.remainingMeals").value(33));

        then(packageGrantService).should().grantPackage(9921L, "MONTH_33", 33, "运营A", 7L);
    }
}
