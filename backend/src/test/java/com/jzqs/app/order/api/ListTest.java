package com.jzqs.app.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
public class ListTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testManualCreateAndList() throws Exception {
        String payload = """
            {
                "customerId": 1,
                "addressId": 1,
                "mealPeriod": "LUNCH",
                "note": "-",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "2026-05-12"
            }
            """;
        
        mockMvc.perform(post("/api/admin/orders/manual-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andDo(print());
            
        mockMvc.perform(get("/api/admin/orders")
                .param("serveDate", "2026-05-12"))
            .andDo(print());
    }
}
