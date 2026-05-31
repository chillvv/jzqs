package com.jzqs.app.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ManualCreateTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldCreateDinnerManualOrderFromMealPeriod() throws Exception {
        String payload = """
            {
                "customerId": 1,
                "addressId": 1,
                "mealPeriod": "DINNER",
                "note": "-",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "2026-06-15"
            }
            """;

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"));

        mockMvc.perform(get("/api/admin/orders")
                .param("serveDate", "2026-06-15"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].mealSummary", containsString("晚餐")));
    }

    @Test
    public void shouldMergeManualOrderQuantityForSameCustomerAddressAndMealPeriod() throws Exception {
        String payload = """
            {
                "customerId": 1,
                "addressId": 1,
                "mealPeriod": "LUNCH",
                "note": "再加一餐",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "2026-06-16"
            }
            """;

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"));

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MERGED"));
    }
}
