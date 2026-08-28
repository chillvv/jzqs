package com.jzqs.app.menu.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.menu.service.MenuWeekAdminService;
import java.util.List;
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

@WebMvcTest(MenuWeekAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class MenuWeekAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @Autowired
    private TestIdempotencyStore testIdempotencyStore;

    @MockBean
    private MenuWeekAdminService menuWeekAdminService;

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
    void shouldReturnCurrentEditableWeek() throws Exception {
        given(menuWeekAdminService.currentWeek()).willReturn(new MenuWeekAdminResponse(
            1L,
            "2026-05-12",
            "2026-05-18",
            "PUBLISHED",
            List.of()
        ));

        mockMvc.perform(get("/api/admin/menu-weeks/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.weekId").value(1L))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void shouldReturnWeekByTargetDate() throws Exception {
        given(menuWeekAdminService.weekByDate("2026-05-26")).willReturn(new MenuWeekAdminResponse(
            3L,
            "2026-05-25",
            "2026-05-31",
            "DRAFT",
            List.of()
        ));

        mockMvc.perform(get("/api/admin/menu-weeks/current").param("targetDate", "2026-05-26"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.weekId").value(3L))
            .andExpect(jsonPath("$.data.weekStartDate").value("2026-05-25"));

        then(menuWeekAdminService).should().weekByDate("2026-05-26");
    }

    @Test
    void shouldCreateNextWeekTemplate() throws Exception {
        given(menuWeekAdminService.createNextWeekTemplate("运营A")).willReturn(new MenuWeekTemplateResponse(
            4L,
            "2026-06-01",
            "2026-06-07",
            "DRAFT"
        ));

        mockMvc.perform(post("/api/admin/menu-weeks").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weekId").value(4L))
            .andExpect(jsonPath("$.data.weekStartDate").value("2026-06-01"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        then(menuWeekAdminService).should().createNextWeekTemplate("运营A");
    }

    @Test
    void shouldSaveDaySlots() throws Exception {
        given(menuWeekAdminService.saveDay(eq(3L), eq("2026-05-25"), any(MenuWeekDaySaveRequest.class))).willReturn(
            new MenuWeekDaySaveResponse(3L, "2026-05-25", "SAVED")
        );

        mockMvc.perform(put("/api/admin/menu-weeks/3/days/2026-05-25")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lunch": {
                        "slotStatus": "ACTIVE",
                        "dishItems": ["黑椒牛柳", "蒜蓉西兰花", "糙米饭"],
                        "totalCalories": 480,
                        "merchantNote": "少油",
                        "imageUrl": "/assets/meal-default.jpeg"
                      },
                      "dinner": {
                        "slotStatus": "REST",
                        "dishItems": [],
                        "totalCalories": null,
                        "merchantNote": "",
                        "imageUrl": ""
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SAVED"))
            .andExpect(jsonPath("$.data.serveDate").value("2026-05-25"));
    }

    @Test
    void shouldPublishWeek() throws Exception {
        given(menuWeekAdminService.publish(3L, "运营A")).willReturn(new MenuWeekPublishResponse(3L, "PUBLISHED"));

        mockMvc.perform(post("/api/admin/menu-weeks/3/publish").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weekId").value(3L))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        then(menuWeekAdminService).should().publish(3L, "运营A");
    }

    @Test
    void shouldCopyFromLastWeek() throws Exception {
        given(menuWeekAdminService.copyFromLastWeek("运营A")).willReturn(new MenuWeekCopyResponse(
            5L,
            "2026-05-26",
            "2026-06-01",
            "DRAFT",
            "2026-05-19"
        ));

        mockMvc.perform(post("/api/admin/menu-weeks/copy-from-last-week").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weekId").value(5L))
            .andExpect(jsonPath("$.data.weekStartDate").value("2026-05-26"))
            .andExpect(jsonPath("$.data.copiedFromWeekStart").value("2026-05-19"));

        then(menuWeekAdminService).should().copyFromLastWeek("运营A");
    }
}
