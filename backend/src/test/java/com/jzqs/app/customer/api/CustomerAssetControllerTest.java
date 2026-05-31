package com.jzqs.app.customer.api;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncRequest;
import com.jzqs.app.customer.service.CustomerMainSheetSyncService;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncSummaryResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(CustomerAssetController.class)
class CustomerAssetControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CustomerAssetService customerAssetService;

    @MockBean
    private CustomerMainSheetSyncService customerMainSheetSyncService;

    @Test
    void shouldCreateCustomerProfile() throws Exception {
        given(customerAssetService.createCustomerProfile(Map.of(
            "name", "新客户",
            "phone", "13800000009",
            "remark", "企业午餐客户",
            "priorityCustomer", true,
            "priorityTag", "企业",
            "priorityNote", "前台统一签收"
        ))).willReturn(Map.of(
            "customerId", 9L,
            "status", "CREATED"
        ));

        mockMvc.perform(post("/api/admin/customers")
                .contentType("application/json")
                .content("""
                    {
                      "name": "新客户",
                      "phone": "13800000009",
                      "remark": "企业午餐客户",
                      "priorityCustomer": true,
                      "priorityTag": "企业",
                      "priorityNote": "前台统一签收"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.customerId").value(9))
            .andExpect(jsonPath("$.data.status").value("CREATED"));

        then(customerAssetService).should().createCustomerProfile(Map.of(
            "name", "新客户",
            "phone", "13800000009",
            "remark", "企业午餐客户",
            "priorityCustomer", true,
            "priorityTag", "企业",
            "priorityNote", "前台统一签收"
        ));
    }

    @Test
    void shouldReturnPagedCustomerAssets() throws Exception {
        given(customerAssetService.listAssets("张", "FORMAL", true, null, true)).willReturn(PageResponse.of(List.of(
            new CustomerAssetResponse(1L, "张先生", "13800000001", "FORMAL", 33, 12, true, true, true, "新开卡优先", "重点关注", "2026-05-13 11:30:00", "2026-05-01 08:00:00", "ACTIVE"),
            new CustomerAssetResponse(2L, "张女士", "13900000002", "FORMAL", 7, 1, true, false, true, "重点照顾", "", "2026-05-12 18:00:00", "2026-05-02 09:00:00", "ACTIVE")
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
        given(customerAssetService.customerDetail(1L)).willReturn(Map.of(
            "id", 1L,
            "name", "张先生",
            "priorityTag", "新开卡优先"
        ));

        mockMvc.perform(get("/api/admin/customers/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.priorityTag").value("新开卡优先"));
    }

    @Test
    void shouldUpdateCustomerProfile() throws Exception {
        given(customerAssetService.updateCustomerProfile(1L, Map.of(
            "name", "张先生",
            "phone", "13800000001",
            "remark", "午餐优先送",
            "priorityCustomer", true,
            "priorityTag", "VIP",
            "priorityNote", "老板重点关注"
        ))).willReturn(Map.of(
            "customerId", 1L,
            "status", "UPDATED"
        ));

        mockMvc.perform(post("/api/admin/customers/1/profile")
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

        then(customerAssetService).should().updateCustomerProfile(1L, Map.of(
            "name", "张先生",
            "phone", "13800000001",
            "remark", "午餐优先送",
            "priorityCustomer", true,
            "priorityTag", "VIP",
            "priorityNote", "老板重点关注"
        ));
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
