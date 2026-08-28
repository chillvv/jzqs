package com.jzqs.app.menu.api;
import static org.mockito.BDDMockito.given;
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
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.service.MenuScheduleService;
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
@WebMvcTest(MenuScheduleController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class MenuScheduleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;
    @Autowired
    private TestIdempotencyStore testIdempotencyStore;
    @MockBean
    private MenuScheduleService menuScheduleService;

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
    void shouldReturnPublishedMenuSchedules() throws Exception {
        given(menuScheduleService.list()).willReturn(PageResponse.of(List.of(
            new MenuScheduleResponse(1L, "2026-05-12", "LUNCH", "香煎鸡胸肉套餐", "香煎鸡胸肉 + 清炒虾仁 + 藜麦饭", 450, "大份/中份", "PUBLISHED"),
            new MenuScheduleResponse(2L, "2026-05-12", "DINNER", "泰式柠檬龙利鱼", "泰式柠檬龙利鱼 + 紫薯泥", 320, "-", "PUBLISHED")
        ), 1, 20, 2));
        mockMvc.perform(get("/api/admin/menu-schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].mealName").value("香煎鸡胸肉套餐"))
            .andExpect(jsonPath("$.data.items[1].mealPeriod").value("DINNER"));
    }

    @Test
    void shouldCreateMenuScheduleWithTypedResponse() throws Exception {
        given(menuScheduleService.create("2026-05-12", "LUNCH", "香煎鸡胸肉套餐", "香煎鸡胸肉 + 清炒虾仁 + 藜麦饭", 450, "大份/中份"))
            .willReturn(new MenuScheduleUpsertResponse(
                3L,
                "2026-05-12",
                "LUNCH",
                "香煎鸡胸肉套餐",
                "香煎鸡胸肉 + 清炒虾仁 + 藜麦饭",
                450,
                "大份/中份",
                "ACTIVE"
            ));

        mockMvc.perform(post("/api/admin/menu-schedules")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "serveDate": "2026-05-12",
                      "mealPeriod": "LUNCH",
                      "mealName": "香煎鸡胸肉套餐",
                      "mealDetail": "香煎鸡胸肉 + 清炒虾仁 + 藜麦饭",
                      "calories": 450,
                      "merchantNote": "大份/中份"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(3L))
            .andExpect(jsonPath("$.data.mealName").value("香煎鸡胸肉套餐"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateMenuScheduleWithTypedResponse() throws Exception {
        given(menuScheduleService.update(3L, "2026-05-12", "DINNER", "泰式柠檬龙利鱼", "泰式柠檬龙利鱼 + 紫薯泥", 320, "少辣"))
            .willReturn(new MenuScheduleUpsertResponse(
                3L,
                "2026-05-12",
                "DINNER",
                "泰式柠檬龙利鱼",
                "泰式柠檬龙利鱼 + 紫薯泥",
                320,
                "少辣",
                "ACTIVE"
            ));

        mockMvc.perform(put("/api/admin/menu-schedules/3")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "serveDate": "2026-05-12",
                      "mealPeriod": "DINNER",
                      "mealName": "泰式柠檬龙利鱼",
                      "mealDetail": "泰式柠檬龙利鱼 + 紫薯泥",
                      "calories": 320,
                      "merchantNote": "少辣"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(3L))
            .andExpect(jsonPath("$.data.mealPeriod").value("DINNER"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldDisableMenuScheduleWithTypedResponse() throws Exception {
        given(menuScheduleService.disable(3L))
            .willReturn(new MenuScheduleStatusResponse(3L, "DISABLED"));

        mockMvc.perform(post("/api/admin/menu-schedules/3/disable").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(3L))
            .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }
}
