package com.jzqs.app.order.api;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(OrderPrepController.class)
class OrderPrepControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private OrderPrepService orderPrepService;
    @MockBean
    private AftersaleService aftersaleService;

    @Test
    void shouldReturnTomorrowPrepStats() throws Exception {
        given(orderPrepService.prepStats()).willReturn(new OrderPrepStatsResponse(3, 2, 1, 2, 1, 1, 1, 1, 1));
        mockMvc.perform(get("/api/admin/orders/prep-stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalMeals").value(3))
            .andExpect(jsonPath("$.data.selfOrderCount").value(2))
            .andExpect(jsonPath("$.data.specialOrderCount").value(1));
    }

    @Test
    void shouldReturnTomorrowOrderPrepList() throws Exception {
        given(orderPrepService.prepPage(any())).willReturn(PageResponse.of(List.of(
            new OrderPrepItemResponse(1L, "张先生", "13800000001", "午餐 / 香煎鸡胸肉套餐", 1, "少饭，不要洋葱", "补偿送果蔬汁", "重点标签", "高新区科技园A座8层", "MINIAPP", true, true, "PENDING_DISPATCH", "PENDING_DISPATCH", "待配送", true, true, false, "已占用"),
            new OrderPrepItemResponse(2L, "李女士", "13900000002", "晚餐 / 泰式柠檬龙利鱼", 1, "-", "", "", "阳光小区3栋2单元", "MINIAPP", false, false, "PENDING_DISPATCH", "REFUND_PROCESSING", "退款处理中", true, true, false, "已占用"),
            new OrderPrepItemResponse(3L, "王总", "13700000003", "午餐 / 香煎鸡胸肉套餐", 2, "微辣", "优先出餐", "VIP袋签", "财富中心写字楼1201", "BACKEND", true, false, "DELIVERED", "DELIVERED", "已完成", false, false, false, "已占用")
        ), 1, 20, 3));
        mockMvc.perform(get("/api/admin/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].customerName").value("张先生"))
            .andExpect(jsonPath("$.data.items[0].adminNote").value("补偿送果蔬汁"))
            .andExpect(jsonPath("$.data.items[1].displayStatus").value("REFUND_PROCESSING"))
            .andExpect(jsonPath("$.data.items[1].displayStatusLabel").value("退款处理中"))
            .andExpect(jsonPath("$.data.items[2].source").value("BACKEND"))
            .andExpect(jsonPath("$.data.items[2].displayStatusLabel").value("已完成"))
            .andExpect(jsonPath("$.data.items[2].quantity").value(2));
    }

    @Test
    void shouldReturnSpecialOrders() throws Exception {
        given(orderPrepService.specialOrders("2026-05-14")).willReturn(List.of(
            new SpecialOrderItem(1L, "张先生", "13800000001", "高新区科技园A座8层", "LUNCH", 1, "少饭", "", "重点标签", false)
        ));

        mockMvc.perform(get("/api/admin/orders/special-orders").param("serveDate", "2026-05-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].customerName").value("张先生"))
            .andExpect(jsonPath("$.data[0].specialTag").value("重点标签"));
    }

    @Test
    void shouldSearchManualCreateCustomersWithAddresses() throws Exception {
        given(orderPrepService.searchManualCreateCustomers("1380000")).willReturn(List.of(
            new ManualCreateCustomerSearchResponse(
                1L,
                "张先生",
                "13800000001",
                15,
                List.of(
                    new ManualCreateCustomerAddressResponse(1L, "高新区科技园A座8层", "高新区", true),
                    new ManualCreateCustomerAddressResponse(10L, "软件园二期6号楼", "高新区", false)
                )
            )
        ));

        mockMvc.perform(get("/api/admin/orders/manual-create/customers").param("keyword", "1380000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].customerName").value("张先生"))
            .andExpect(jsonPath("$.data[0].customerPhone").value("13800000001"))
            .andExpect(jsonPath("$.data[0].remainingMeals").value(15))
            .andExpect(jsonPath("$.data[0].addresses.length()").value(2))
            .andExpect(jsonPath("$.data[0].addresses[0].addressLine").value("高新区科技园A座8层"))
            .andExpect(jsonPath("$.data[0].addresses[0].isDefault").value(true));
    }

    @Test
    void shouldUpdateOrderProfile() throws Exception {
        given(orderPrepService.updateOrderProfile(1L, Map.of(
            "mealPeriod", "DINNER",
            "quantity", 2,
            "deliveryAddress", "高新区软件园G座",
            "adminNote", "换成低卡版",
            "specialTag", "VIP袋签"
        ))).willReturn(Map.of(
            "orderId", 1L,
            "status", "UPDATED"
        ));

        mockMvc.perform(post("/api/admin/orders/1/profile")
                .contentType("application/json")
                .content("""
                    {
                      "mealPeriod": "DINNER",
                      "quantity": 2,
                      "deliveryAddress": "高新区软件园G座",
                      "adminNote": "换成低卡版",
                      "specialTag": "VIP袋签"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.status").value("UPDATED"));

        then(orderPrepService).should().updateOrderProfile(1L, Map.of(
            "mealPeriod", "DINNER",
            "quantity", 2,
            "deliveryAddress", "高新区软件园G座",
            "adminNote", "换成低卡版",
            "specialTag", "VIP袋签"
        ));
    }
}
