package com.jzqs.app.menu.api;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.service.MenuScheduleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(MenuScheduleController.class)
class MenuScheduleControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private MenuScheduleService menuScheduleService;

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
}
