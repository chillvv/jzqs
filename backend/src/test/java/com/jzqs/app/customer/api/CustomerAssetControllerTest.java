package com.jzqs.app.customer.api;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.InMemoryIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncRequest;
import com.jzqs.app.customer.service.CustomerMainSheetSyncService;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncSummaryResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(CustomerAssetController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    InMemoryIdempotencyStore.class
})
class CustomerAssetControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;
    @Autowired
    private InMemoryIdempotencyStore inMemoryIdempotencyStore;
    @MockBean
    private CustomerAssetService customerAssetService;

    @MockBean
    private CustomerMainSheetSyncService customerMainSheetSyncService;

    @AfterEach
    void tearDown() {
        inMemoryRateLimitStore.clear();
        inMemoryIdempotencyStore.clear();
    }

    @Test
    void shouldCreateCustomerProfile() throws Exception {
        CustomerProfileCreateRequest request = new CustomerProfileCreateRequest(
            "新客户",
            "13800000009",
            "企业午餐客户",
            "FORMAL",
            "高新区软件园前台",
            null,
            null,
            null,
            true,
            "企业",
            "前台统一签收"
        );
        given(customerAssetService.createCustomerProfile(request))
            .willReturn(new CustomerProfileCreateResponse(9L, "CREATED"));

        mockMvc.perform(post("/api/admin/customers")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "name": "新客户",
                      "phone": "13800000009",
                      "remark": "企业午餐客户",
                      "customerStatus": "FORMAL",
                      "addressLine": "高新区软件园前台",
                      "priorityCustomer": true,
                      "priorityTag": "企业",
                      "priorityNote": "前台统一签收"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(9))
            .andExpect(jsonPath("$.data.status").value("CREATED"));

        then(customerAssetService).should().createCustomerProfile(request);
    }

    @Test
    void shouldReturnPagedCustomerAssets() throws Exception {
        given(customerAssetService.listAssets("张", "FORMAL", true, null, true)).willReturn(PageResponse.of(List.of(
            new CustomerAssetResponse(1L, "张先生", "13800000001", "FORMAL", 33, 12, true, true, true, "新开卡优先", "重点关注", "2026-05-01 09:00:00", "2026-06-20 00:00:00", 5, "EXPIRING_SOON", "即将到期", "2026-05-13 11:30:00", "2026-05-01 08:00:00", "ACTIVE"),
            new CustomerAssetResponse(2L, "张女士", "13900000002", "FORMAL", 7, 1, true, false, true, "重点照顾", "", "2026-04-25 09:00:00", "2026-05-25 00:00:00", -1, "EXPIRED", "已过期", "2026-05-12 18:00:00", "2026-05-02 09:00:00", "ACTIVE")
        ), 1, 20, 3));
        mockMvc.perform(get("/api/admin/customers/assets")
                .param("keyword", "张")
                .param("customerStatus", "FORMAL")
                .param("hasBalance", "true")
                .param("priorityCustomer", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].customerStatus").value("FORMAL"))
            .andExpect(jsonPath("$.data.items[0].packageName").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].priorityCustomer").value(true))
            .andExpect(jsonPath("$.data.items[1].status").value("ACTIVE"));
    }

    @Test
    void shouldReturnCustomerDetail() throws Exception {
        given(customerAssetService.customerDetail(1L)).willReturn(new CustomerDetailResponse(
            1L,
            "张先生",
            "13800000001",
            "FORMAL",
            "重点客户",
            true,
            "新开卡优先",
            "前台签收",
            12,
            "2026-05-01 09:00:00",
            "2026-06-20",
            5,
            "2026-05-01 08:00:00",
            "2026-05-13 11:30:00",
            null,
            List.of(),
            List.of(),
            List.of()
        ));

        mockMvc.perform(get("/api/admin/customers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.priorityTag").value("新开卡优先"));
    }

    @Test
    void shouldUpdateCustomerProfile() throws Exception {
        CustomerProfileUpdateRequest request = new CustomerProfileUpdateRequest(
            "张先生",
            "13800000001",
            "午餐优先送",
            null,
            null,
            null,
            null,
            null,
            true,
            "VIP",
            "老板重点关注"
        );
        given(customerAssetService.updateCustomerProfile(1L, request))
            .willReturn(new CustomerProfileUpdateResponse(1L, "UPDATED"));

        mockMvc.perform(post("/api/admin/customers/1/profile")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "name": "张先生",
                      "phone": "13800000001",
                      "remark": "午餐优先送",
                      "priorityCustomer": true,
                      "priorityTag": "VIP",
                      "priorityNote": "老板重点关注"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(1))
            .andExpect(jsonPath("$.data.status").value("UPDATED"));

        then(customerAssetService).should().updateCustomerProfile(1L, request);
    }

    @Test
    void shouldCreateCustomerAddress() throws Exception {
        CustomerAddressUpsertRequest request = new CustomerAddressUpsertRequest(
            "前台",
            "13800000001",
            "高新区软件园A座",
            "高新区",
            true
        );
        given(customerAssetService.createCustomerAddress(1L, request))
            .willReturn(new CustomerAddressActionResponse(1L, 12L, "CREATED"));

        mockMvc.perform(post("/api/admin/customers/1/addresses")
                .contentType("application/json")
                .content("""
                    {
                      "contactName": "前台",
                      "contactPhone": "13800000001",
                      "addressLine": "高新区软件园A座",
                      "areaCode": "高新区",
                      "isDefault": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(1))
            .andExpect(jsonPath("$.data.addressId").value(12))
            .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void shouldUpdateCustomerAddress() throws Exception {
        CustomerAddressUpsertRequest request = new CustomerAddressUpsertRequest(
            "前台",
            "13900000001",
            "高新区软件园B座",
            "高新区",
            false
        );
        given(customerAssetService.updateCustomerAddress(1L, 12L, request))
            .willReturn(new CustomerAddressActionResponse(1L, 12L, "UPDATED"));

        mockMvc.perform(post("/api/admin/customers/1/addresses/12")
                .contentType("application/json")
                .content("""
                    {
                      "contactName": "前台",
                      "contactPhone": "13900000001",
                      "addressLine": "高新区软件园B座",
                      "areaCode": "高新区",
                      "isDefault": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(1))
            .andExpect(jsonPath("$.data.addressId").value(12))
            .andExpect(jsonPath("$.data.status").value("UPDATED"));
    }

    @Test
    void shouldDeleteCustomerAddress() throws Exception {
        given(customerAssetService.deleteCustomerAddress(1L, 12L))
            .willReturn(new CustomerAddressActionResponse(1L, 12L, "DELETED"));

        mockMvc.perform(delete("/api/admin/customers/1/addresses/12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(1))
            .andExpect(jsonPath("$.data.addressId").value(12))
            .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    void shouldDeductWalletMealsWithoutValidityDays() throws Exception {
        WalletAdjustRequest request = new WalletAdjustRequest(1, null, "运营A", "手工扣减");
        given(customerAssetService.deductMeals(1L, request))
            .willReturn(new CustomerWalletAdjustResponse(1L, 8, "ACTIVE"));

        mockMvc.perform(post("/api/admin/customers/1/wallet/deduct")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "mealDelta": 1,
                      "remark": "手工扣减"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(1))
            .andExpect(jsonPath("$.data.remainingMeals").value(8))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        then(customerAssetService).should().deductMeals(1L, request);
    }

    @Test
    void shouldReturnRemarkSuggestionsByScene() throws Exception {
        given(customerAssetService.remarkSuggestions("WALLET_REMARK", null)).willReturn(
            new RemarkSuggestionResponse("WALLET_REMARK", List.of("补餐", "续卡赠送", "客户转账补 10 餐"))
        );

        mockMvc.perform(get("/api/admin/customers/remark-suggestions")
                .param("scene", "WALLET_REMARK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.scene").value("WALLET_REMARK"))
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0]").value("补餐"));
    }

    @Test
    void shouldTriggerMainSheetSync() throws Exception {
        CustomerMainSheetSyncRequest request = new CustomerMainSheetSyncRequest(
            true,
            "d:\\Code\\jzqs\\简知扣餐表26年5月.xlsx"
        );
        given(customerMainSheetSyncService.sync(request)).willReturn(
            new CustomerMainSheetSyncSummaryResponse(379, 2, 379, "d:\\Code\\jzqs\\简知扣餐表26年5月.xlsx")
        );

        mockMvc.perform(post("/api/admin/customers/sync-main-sheet")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType("application/json")
                .content("""
                    {
                      "clearExisting": true,
                      "filePath": "d:\\\\Code\\\\jzqs\\\\简知扣餐表26年5月.xlsx"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.importedCount").value(379))
            .andExpect(jsonPath("$.data.skippedCount").value(2))
            .andExpect(jsonPath("$.data.walletCount").value(379));
    }
}
