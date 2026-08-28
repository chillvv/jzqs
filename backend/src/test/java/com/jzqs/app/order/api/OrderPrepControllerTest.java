package com.jzqs.app.order.api;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
@WebMvcTest(OrderPrepController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class OrderPrepControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;
    @Autowired
    private TestIdempotencyStore testIdempotencyStore;
    @MockBean
    private OrderPrepService orderPrepService;
    @MockBean
    private AftersaleService aftersaleService;
    @MockBean
    private com.jzqs.app.mobile.DeliveryReleaseSupport deliveryReleaseSupport;
    @MockBean
    private com.jzqs.app.mobile.MobilePortalService mobilePortalService;

    private RequestPostProcessor admin() {
        return request -> {
            request.setAttribute("userId", 7L);
            request.setAttribute("userType", "admin");
            request.setAttribute("adminDisplayName", "运营A");
            return request;
        };
    }

    @AfterEach
    void tearDown() {
        inMemoryRateLimitStore.clear();
    }

    @Test
    void shouldReturnTomorrowPrepStats() throws Exception {
        given(orderPrepService.prepStats("2026-05-13")).willReturn(new OrderPrepStatsResponse(3, 2, 1, 2, 1, 1, 1, 1));
        mockMvc.perform(get("/api/admin/orders/prep-stats").param("serveDate", "2026-05-13"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalMeals").value(3))
            .andExpect(jsonPath("$.data.selfOrderCount").value(2))
            .andExpect(jsonPath("$.data.labelRequiredCount").value(1));
    }

    @Test
    void shouldReturnTomorrowOrderPrepList() throws Exception {
        given(orderPrepService.prepPage(any())).willReturn(PageResponse.of(List.of(
            new OrderPrepItemResponse(1L, "张先生", "13800000001", "LUNCH", "LUNCH", "午餐 / 香煎鸡胸肉套餐", 1, "少饭，不要洋葱", "补偿送果蔬汁", "高新区科技园A座8层", "MINIAPP", "高新区", "骑手小李", true, true, "PENDING_DISPATCH", "PENDING_DISPATCH", "待配送", "", true, true, false, "", "", "", null, 0L, "2026-05-13"),
            new OrderPrepItemResponse(2L, "李女士", "13900000002", "DINNER", "DINNER", "晚餐 / 泰式柠檬龙利鱼", 1, "-", "", "阳光小区3栋2单元", "MINIAPP", "高新区", "骑手小李", false, false, "PENDING_DISPATCH", "REFUND_PROCESSING", "退款处理中", "", true, true, false, "", "", "", null, 0L, "2026-05-13"),
            new OrderPrepItemResponse(3L, "王总", "13700000003", "LUNCH", "LUNCH", "午餐 / 香煎鸡胸肉套餐", 2, "微辣", "优先出餐", "财富中心写字楼1201", "BACKEND", "高新区", "", true, false, "DELIVERED", "DELIVERED", "已完成", "", false, false, false, "", "", "", "2026-05-14 12:30:00", 0L, "2026-05-13")
        ), 1, 20, 3));
        mockMvc.perform(get("/api/admin/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].customerName").value("张先生"))
            .andExpect(jsonPath("$.data.items[0].merchantRemark").value("补偿送果蔬汁"))
            .andExpect(jsonPath("$.data.items[1].displayStatus").value("REFUND_PROCESSING"))
            .andExpect(jsonPath("$.data.items[1].displayStatusLabel").value("退款处理中"))
            .andExpect(jsonPath("$.data.items[2].source").value("BACKEND"))
            .andExpect(jsonPath("$.data.items[2].displayStatusLabel").value("已完成"))
            .andExpect(jsonPath("$.data.items[2].quantity").value(2));
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
        OrderProfileUpdateRequest request = new OrderProfileUpdateRequest(
            "DINNER",
            2,
            "高新区软件园G座",
            "换成低卡版",
            true,
            "DELIVERED"
        );
        given(orderPrepService.updateOrderProfile(1L, request))
            .willReturn(new OrderProfileUpdateResponse(1L, "UPDATED", 18L));

        mockMvc.perform(post("/api/admin/orders/1/profile")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "mealPeriod": "DINNER",
                      "quantity": 2,
                      "deliveryAddress": "高新区软件园G座",
                      "merchantRemark": "换成低卡版",
                      "priorityCustomer": true,
                      "status": "DELIVERED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.status").value("UPDATED"))
            .andExpect(jsonPath("$.data.addressId").value(18));

        then(orderPrepService).should().updateOrderProfile(1L, request);
    }

    @Test
    void shouldIgnoreLegacySpecialTagWhenUpdatingMerchantRemark() throws Exception {
        given(orderPrepService.updateMerchantRemark(12L, new OrderMerchantRemarkUpdateRequest("商家备注")))
            .willReturn(new OrderMerchantRemarkUpdateResponse(12L, "UPDATED"));

        mockMvc.perform(post("/api/admin/orders/12/merchant-remark")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "merchantRemark": "商家备注",
                      "specialTag": "加急"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(12L))
            .andExpect(jsonPath("$.data.status").value("UPDATED"));

        then(orderPrepService).should().updateMerchantRemark(12L, new OrderMerchantRemarkUpdateRequest("商家备注"));
    }

    @Test
    void shouldRejectBlankSpecialDispatchMealPeriod() throws Exception {
        mockMvc.perform(post("/api/admin/orders/9/special-dispatch")
                .contentType("application/json")
                .content("""
                    {
                      "deliveryMealPeriod": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidBulkImportItems() throws Exception {
        mockMvc.perform(post("/api/admin/orders/bulk-import-subscription")
                .contentType("application/json")
                .content("""
                    {
                      "serveDate": "2026-06-26",
                      "items": [
                        {
                          "customerId": 1,
                          "mealPeriod": "",
                          "deliveryMealPeriod": "DINNER",
                          "addressId": 1,
                          "note": "默认备注"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnGroupedOrderNotes() throws Exception {
        given(orderPrepService.orderNotes(1L)).willReturn(new OrderNotesResponse(
            List.of(
                new OrderNoteItemResponse(11L, "USER", "CUSTOMER_PROFILE", "SNAPSHOT", "长期少饭", "ACTIVE", "2026-06-10T08:00:00"),
                new OrderNoteItemResponse(12L, "USER", "CUSTOMER_ORDER_INPUT", "SNAPSHOT", "本次不要辣", "ACTIVE", "2026-06-10T08:01:00")
            ),
            List.of(
                new OrderNoteItemResponse(13L, "MERCHANT", "MERCHANT_PROFILE", "SNAPSHOT", "重点关注", "ACTIVE", "2026-06-10T08:00:00"),
                new OrderNoteItemResponse(14L, "MERCHANT", "MERCHANT_ORDER_ONCE", "ORDER_ONCE", "本餐送果蔬汁", "ACTIVE", "2026-06-10T08:02:00")
            )
        ));

        mockMvc.perform(get("/api/admin/orders/1/notes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userNotes.length()").value(2))
            .andExpect(jsonPath("$.data.userNotes[0].sourceType").value("CUSTOMER_PROFILE"))
            .andExpect(jsonPath("$.data.merchantNotes.length()").value(2))
            .andExpect(jsonPath("$.data.merchantNotes[1].scopeType").value("ORDER_ONCE"))
            .andExpect(jsonPath("$.data.merchantNotes[1].content").value("本餐送果蔬汁"));
    }

    @Test
    void shouldAddOneTimeMerchantOrderNote() throws Exception {
        given(orderPrepService.addOrderNote(1L, new OrderNoteCreateRequest("MERCHANT", "ORDER_ONCE", "本餐送果蔬汁")))
            .willReturn(new OrderNoteCreateResponse(1L, "CREATED"));

        mockMvc.perform(post("/api/admin/orders/1/notes")
                .with(admin())
                .contentType("application/json")
                .content("""
                    {
                      "noteType": "MERCHANT",
                      "scopeType": "ORDER_ONCE",
                      "content": "本餐送果蔬汁"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.status").value("CREATED"));

        then(orderPrepService).should().addOrderNote(1L, new OrderNoteCreateRequest("MERCHANT", "ORDER_ONCE", "本餐送果蔬汁"));
    }

    @Test
    void shouldCreateAfterSaleWithTypedRequest() throws Exception {
        given(aftersaleService.createCase(any(AdminAftersaleCreateRequest.class)))
            .willReturn(new AdminAftersaleCreateResponse(21L, "PENDING"));

        mockMvc.perform(post("/api/admin/orders/9/after-sales")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "type": "COMPENSATION",
                      "reasonCode": "ADMIN_DIRECT",
                      "reasonText": "餐品撒漏",
                      "issueParamSummary": "补一杯饮品",
                      "remark": "优先处理"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.afterSaleId").value(21L))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        then(aftersaleService).should().createCase(new AdminAftersaleCreateRequest(
            9L,
            "COMPENSATION",
            "ADMIN_DIRECT",
            "餐品撒漏",
            "补一杯饮品",
            0,
            "NORMAL",
            "优先处理",
            "运营A"
        ));
    }

    @Test
    void shouldRateLimitRepeatedCancelRequests() throws Exception {
        given(aftersaleService.createCase(any(AdminAftersaleCreateRequest.class)))
            .willReturn(new AdminAftersaleCreateResponse(21L, "PENDING"));

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/admin/orders/9/after-sales")
                    .requestAttr("userId", 7L)
                    .requestAttr("userType", "admin")
                    .requestAttr("adminDisplayName", "运营A")
                    .contentType("application/json")
                    .content("""
                        {
                          "type": "COMPENSATION",
                          "reasonCode": "ADMIN_DIRECT",
                          "reasonText": "餐品撒漏-%s",
                          "issueParamSummary": "补一杯饮品",
                          "remark": "优先处理"
                        }
                        """.formatted(attempt)))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/admin/orders/9/after-sales")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "type": "COMPENSATION",
                      "reasonCode": "ADMIN_DIRECT",
                      "reasonText": "餐品撒漏-5",
                      "issueParamSummary": "补一杯饮品",
                      "remark": "优先处理"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
