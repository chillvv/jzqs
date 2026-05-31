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

import com.jzqs.app.menu.service.MenuWeekAdminService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MenuWeekAdminController.class)
class MenuWeekAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuWeekAdminService menuWeekAdminService;

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
        given(menuWeekAdminService.createNextWeekTemplate("system")).willReturn(Map.of(
            "weekId", 4L,
            "status", "DRAFT"
        ));

        mockMvc.perform(post("/api/admin/menu-weeks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weekId").value(4L))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void shouldSaveDaySlots() throws Exception {
        given(menuWeekAdminService.saveDay(eq(3L), eq("2026-05-25"), any(MenuWeekDaySaveRequest.class))).willReturn(Map.of(
            "weekId", 3L,
            "serveDate", "2026-05-25",
            "status", "SAVED"
        ));

        mockMvc.perform(put("/api/admin/menu-weeks/3/days/2026-05-25")
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
        given(menuWeekAdminService.publish(3L, "system")).willReturn(Map.of(
            "weekId", 3L,
            "status", "PUBLISHED"
        ));

        mockMvc.perform(post("/api/admin/menu-weeks/3/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weekId").value(3L))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }
}
