package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.MobilePortalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileCustomerController.class)
class MobileCustomerControllerTest {
    private static final String AUTH_HEADER = "Bearer " + JwtUtils.generateToken(Map.of(
        "userId", 1L,
        "customerId", 1L,
        "userType", "customer"
    ));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MobilePortalService mobilePortalService;

    @MockBean
    private CustomerAssetService customerAssetService;

    @Test
    void shouldReturnCustomerHome() throws Exception {
        given(mobilePortalService.customerHome(1L)).willReturn(
            new MobileHomeResponse(1L, "张先生", "13800000001", "未开通套餐", 0, 12, true, "可下单", "节假日公告", "照常营业", "高新区科技园A座8层", "少饭，多蔬菜", List.of(), false, "")
        );

        mockMvc.perform(get("/api/mobile/customer/home").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("张先生"))
            .andExpect(jsonPath("$.data.remainingMeals").value(12))
            .andExpect(jsonPath("$.data.remark").value("少饭，多蔬菜"));
    }

    @Test
    void shouldReturnGuestHomeWithoutAuthorization() throws Exception {
        given(mobilePortalService.guestHome()).willReturn(
            new MobileHomeResponse(0L, "微信用户", "", "未开通套餐", 0, 0, true, "可浏览菜单", "节假日公告", "照常营业", "", "", List.of(), false, "")
        );

        mockMvc.perform(get("/api/mobile/customer/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.name").value("微信用户"))
            .andExpect(jsonPath("$.data.remainingMeals").value(0))
            .andExpect(jsonPath("$.data.orderingStatusLabel").value("可浏览菜单"));
    }

    @Test
    void shouldCreateMiniappOrder() throws Exception {
        given(mobilePortalService.createMiniappOrder(
            eq(1L),
            eq("2026-05-13"),
            eq("LUNCH"),
            eq("高新区科技园A座8层"),
            eq("少饭")
        )).willReturn(Map.of("orderId", 1001L, "status", "PENDING_DISPATCH"));

        mockMvc.perform(post("/api/mobile/customer/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH_HEADER)
                .content("""
                    {
                      "serveDate": "2026-05-13",
                      "mealPeriod": "LUNCH",
                      "deliveryAddress": "高新区科技园A座8层",
                      "note": "少饭"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1001L))
            .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"));
    }

    @Test
    void shouldCreateMobileAfterSale() throws Exception {
        given(mobilePortalService.createAfterSale(eq(1L), eq(8L), any(MobileCreateAfterSaleRequest.class)))
            .willReturn(Map.of("afterSaleId", 99L, "status", "PENDING"));

        mockMvc.perform(post("/api/mobile/customer/orders/8/after-sales")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH_HEADER)
                .content("""
                    {
                      "type": "REFUND",
                      "reasonCode": "USER_TEMP_CHANGE",
                      "reasonText": "临时有事，不需要明天午餐",
                      "remark": "希望退回餐次"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.afterSaleId").value(99L))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldReturnCustomerAfterSales() throws Exception {
        given(mobilePortalService.customerAfterSales(1L)).willReturn(List.of(
            new MobileAfterSaleItemResponse(5L, 8L, "REFUND", "PENDING", "USER_TEMP_CHANGE", "临时有事", "", "2026-05-26 20:30:00", null)
        ));

        mockMvc.perform(get("/api/mobile/customer/after-sales").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void shouldReturnWeekMenus() throws Exception {
        given(mobilePortalService.weekMenus("2026-05-12")).willReturn(List.of(
            new MobileWeekMenuDayResponse(
                "2026-05-12",
                "周二",
                List.of(
                    new MobileMenuItemResponse(
                        1L,
                        "2026-05-12",
                        "LUNCH",
                        List.of("香煎鸡胸肉", "清炒时蔬"),
                        420,
                        "少油少盐",
                        "PUBLISHED"
                    )
                )
            )
        ));

        mockMvc.perform(get("/api/mobile/customer/menus/week").param("startDate", "2026-05-12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].weekdayLabel").value("周二"))
            .andExpect(jsonPath("$.data[0].items[0].dishItems[0]").value("香煎鸡胸肉"));
    }

    @Test
    void shouldReturnCurrentWeekMenu() throws Exception {
        given(mobilePortalService.currentWeekMenu()).willReturn(new MobileCurrentWeekResponse(
            "2026-05-18",
            "2026-05-24",
            List.of()
        ));

        mockMvc.perform(get("/api/mobile/customer/menu/current-week"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.weekStartDate").value("2026-05-18"))
            .andExpect(jsonPath("$.data.weekEndDate").value("2026-05-24"));
    }

    @Test
    void shouldReturnTomorrowMenu() throws Exception {
        given(mobilePortalService.tomorrowMenu()).willReturn(new MobileTomorrowMenuResponse(
            "2026-05-13",
            true,
            "",
            new MobileMenuItemResponse(1L, "2026-05-13", "LUNCH", List.of("黑椒牛柳", "蒜蓉西兰花", "糙米饭"), 480, "少油", "ACTIVE"),
            null,
            true,
            "可下单"
        ));

        mockMvc.perform(get("/api/mobile/customer/menu/tomorrow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.serveDate").value("2026-05-13"))
            .andExpect(jsonPath("$.data.selfOrderEnabled").value(true))
            .andExpect(jsonPath("$.data.lunchItem.dishItems[0]").value("黑椒牛柳"))
            .andExpect(jsonPath("$.data.lunchItem.totalCalories").value(480));
    }

    @Test
    void shouldReturnCustomerOrders() throws Exception {
        given(mobilePortalService.customerOrders(1L, null)).willReturn(PageResponse.of(List.of(
            new MobileOrderItemResponse(
                1L,
                "2026-05-12",
                "LUNCH",
                "香煎鸡胸肉套餐",
                "香煎鸡胸肉 + 清炒虾仁 + 藜麦饭",
                "大份/中份",
                "少饭",
                "高新区科技园A座8层",
                "MINIAPP",
                "DELIVERED",
                "https://cos.example.com/r1.jpg",
                "已放前台",
                "2026-05-12 12:05:00",
                false,
                false,
                "",
                "",
                ""
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/customer/orders").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].status").value("DELIVERED"))
            .andExpect(jsonPath("$.data.items[0].mealName").value("香煎鸡胸肉套餐"))
            .andExpect(jsonPath("$.data.items[0].receiptVisible").value(false));
    }

    @Test
    void shouldCancelOrder() throws Exception {
        given(mobilePortalService.cancelMiniappOrder(1L, 3L))
            .willReturn(Map.of("orderId", 3L, "status", "CANCELLED"));

        mockMvc.perform(post("/api/mobile/customer/orders/3/cancel").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.orderId").value(3L))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldReturnRemarkSuggestions() throws Exception {
        given(customerAssetService.remarkSuggestions("ORDER_REMARK", 1L)).willReturn(
            new RemarkSuggestionResponse("ORDER_REMARK", List.of("少饭，多蔬菜", "不要辣"))
        );

        mockMvc.perform(get("/api/mobile/customer/remark-suggestions")
                .header("Authorization", AUTH_HEADER)
                .param("scene", "ORDER_REMARK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.scene").value("ORDER_REMARK"))
            .andExpect(jsonPath("$.data.items[0]").value("少饭，多蔬菜"));
    }

    @Test
    void shouldReturnAddresses() throws Exception {
        given(mobilePortalService.customerAddresses(1L)).willReturn(List.of(
            new MobileAddressResponse(1L, "张先生", "13800000001", "高新区科技园A座8层", "高新区", true)
        ));

        mockMvc.perform(get("/api/mobile/customer/addresses").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].addressLine").value("高新区科技园A座8层"))
            .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void shouldSaveAddress() throws Exception {
        given(mobilePortalService.saveCustomerAddress(
            eq(1L),
            eq("张先生"),
            eq("13800000001"),
            eq("高新区科技园A座8层"),
            eq("高新区"),
            eq(true)
        )).willReturn(new MobileAddressResponse(1L, "张先生", "13800000001", "高新区科技园A座8层", "高新区", true));

        mockMvc.perform(post("/api/mobile/customer/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH_HEADER)
                .content("""
                    {
                      "contactName": "张先生",
                      "contactPhone": "13800000001",
                      "addressLine": "高新区科技园A座8层",
                      "areaCode": "高新区",
                      "isDefault": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.contactName").value("张先生"))
            .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void shouldSetDefaultAddress() throws Exception {
        given(mobilePortalService.setDefaultAddress(1L, 2L))
            .willReturn(Map.of("addressId", 2L, "status", "DEFAULT_UPDATED"));

        mockMvc.perform(post("/api/mobile/customer/addresses/2/default").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.addressId").value(2L))
            .andExpect(jsonPath("$.data.status").value("DEFAULT_UPDATED"));
    }

    @Test
    void shouldReturnWalletTransactions() throws Exception {
        given(mobilePortalService.walletTransactions(1L)).willReturn(PageResponse.of(List.of(
            new WalletTransactionResponse(
                1L,
                1L,
                "RESERVE",
                -1,
                "小程序",
                "用户自主下单占用餐次",
                8L,
                11L,
                9L,
                true,
                "USER_TEMP_CHANGE",
                "临时有事，不需要这餐",
                "2026-05-12 10:00:00"
            )
        ), 1, 20, 1));

        mockMvc.perform(get("/api/mobile/customer/wallet-transactions").header("Authorization", AUTH_HEADER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].transactionType").value("RESERVE"))
            .andExpect(jsonPath("$.data.items[0].refunded").value(true))
            .andExpect(jsonPath("$.data.items[0].relatedTransactionId").value(9L))
            .andExpect(jsonPath("$.data.items[0].refundReasonText").value("临时有事，不需要这餐"));
    }
}
