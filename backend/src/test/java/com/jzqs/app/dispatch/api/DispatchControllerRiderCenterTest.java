package com.jzqs.app.dispatch.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.InMemoryIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.service.DispatchService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DispatchController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    InMemoryIdempotencyStore.class
})
class DispatchControllerRiderCenterTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;
    @Autowired
    private InMemoryIdempotencyStore inMemoryIdempotencyStore;

    @MockBean
    private DispatchService dispatchService;

    @AfterEach
    void tearDown() {
        inMemoryRateLimitStore.clear();
        inMemoryIdempotencyStore.clear();
    }

    @Test
    void shouldReturnDispatchOverviewWithBatchAndReminderStats() throws Exception {
        given(dispatchService.overview(eq("LUNCH"), any())).willReturn(new DispatchOverviewResponse(
            12,
            4,
            3
        ));

        mockMvc.perform(get("/api/admin/dispatch/overview").param("mealPeriod", "LUNCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pendingCount").value(12))
            .andExpect(jsonPath("$.data.dispatchingCount").value(4))
            .andExpect(jsonPath("$.data.missingRiderAreaCount").value(3));
    }

    @Test
    void shouldReturnDispatchBatches() throws Exception {
        given(dispatchService.batches("2026-05-15", "LUNCH")).willReturn(List.of(
            new DispatchBatchResponse(
                11L,
                "2026-05-15",
                "LUNCH",
                1L,
                "王师傅",
                "高新区",
                "IN_PROGRESS",
                8,
                2,
                3,
                "张先生",
                "王先生"
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/batches")
                .param("serveDate", "2026-05-15")
                .param("mealPeriod", "LUNCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].batchStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data[0].currentCustomerName").value("张先生"));
    }

    @Test
    void shouldReturnDispatchExceptions() throws Exception {
        given(dispatchService.exceptions()).willReturn(List.of(
            new DispatchExceptionItemResponse(
                22L,
                "NEW_ADDRESS",
                "未命中地址归属记忆",
                "张先生",
                "13800000001",
                "高新区软件园",
                "高新区",
                "王师傅",
                false
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/exceptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].mealSlotOrderId").value(22))
            .andExpect(jsonPath("$.data[0].suggestedRiderName").value("王师傅"));
    }

    @Test
    void shouldReturnPendingDispatchItems() throws Exception {
        given(dispatchService.pendingItems(eq("LUNCH"), any())).willReturn(List.of(
            new DispatchPendingItemResponse(
                101L,
                "张先生",
                "高新区科技园A座8层"
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/pending-items").param("mealPeriod", "LUNCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].orderId").value(101L))
            .andExpect(jsonPath("$.data[0].customerName").value("张先生"))
            .andExpect(jsonPath("$.data[0].deliveryAddress").value("高新区科技园A座8层"));
    }

    @Test
    void shouldBatchAssignPendingDispatchItems() throws Exception {
        given(dispatchService.batchAssignPendingOrders(
            List.of(101L, 102L),
            "高新区",
            "运营A"
        )).willReturn(new BatchOperationResponse(2, 0, List.of()));

        mockMvc.perform(post("/api/admin/dispatch/pending-items/batch-assign")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderIds": [101, 102],
                      "areaCode": "高新区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.successCount").value(2))
            .andExpect(jsonPath("$.data.failureCount").value(0));

        then(dispatchService).should().batchAssignPendingOrders(List.of(101L, 102L), "高新区", "运营A");
    }

    @Test
    void shouldReorderAreaOrders() throws Exception {
        given(dispatchService.reorderAreaOrders(
            "高新区",
            List.of(
                new DispatchOrderReorderItemRequest(101L, 1),
                new DispatchOrderReorderItemRequest(102L, 2)
            )
        )).willReturn(new DispatchAreaOrdersReorderResponse("高新区", 2));

        mockMvc.perform(post("/api/admin/dispatch/areas/高新区/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .param("mealPeriod", "LUNCH")
                .content("""
                    [
                      { "orderId": 101, "sequenceNumber": 1 },
                      { "orderId": 102, "sequenceNumber": 2 }
                    ]
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.updatedCount").value(2));
    }

    @Test
    void shouldMoveOrderToAnotherArea() throws Exception {
        given(dispatchService.moveOrderToArea(
            "高新区",
            101L,
            "商务区",
            "运营A"
        )).willReturn(new DispatchOrderAreaMoveResponse("高新区", 101L, "商务区"));

        mockMvc.perform(post("/api/admin/dispatch/areas/高新区/orders/101/move")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .param("mealPeriod", "LUNCH")
                .content("""
                    {
                      "targetAreaCode": "商务区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.orderId").value(101L))
            .andExpect(jsonPath("$.data.targetAreaCode").value("商务区"));

        then(dispatchService).should().moveOrderToArea("高新区", 101L, "商务区", "运营A");
    }

    @Test
    void shouldResolveDispatchException() throws Exception {
        given(dispatchService.assignOrder(22L, "王师傅", "高新区"))
            .willReturn(new DispatchOrderAssignResponse(22L, "王师傅", "DISPATCHED"));

        mockMvc.perform(post("/api/admin/dispatch/exceptions/22/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "王师傅",
                      "areaCode": "高新区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(22))
            .andExpect(jsonPath("$.data.status").value("DISPATCHED"));
    }

    @Test
    void shouldReturnPendingRiders() throws Exception {
        given(dispatchService.pendingRiders()).willReturn(List.of(
            new PendingRiderResponse(1L, "骑手小李", "13800000009", "rider_test", "UNASSIGNED", "2026-05-15T20:00:00", "2026-05-15T20:03:00")
        ));

        mockMvc.perform(get("/api/admin/dispatch/pending-riders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].displayName").value("骑手小李"))
            .andExpect(jsonPath("$.data[0].phone").value("13800000009"))
            .andExpect(jsonPath("$.data[0].authStatus").value("UNASSIGNED"));
    }

    @Test
    void shouldActivatePendingRider() throws Exception {
        given(dispatchService.activateRider(1L, "骑手小李", "高新区", "运营A"))
            .willReturn(new DispatchRiderActivateResponse(1L, "ACTIVE", "高新区"));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/activate")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小李",
                      "areaCode": "高新区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"));

        then(dispatchService).should().activateRider(1L, "骑手小李", "高新区", "运营A");
    }

    @Test
    void shouldReturnManagedRiders() throws Exception {
        given(dispatchService.managedRiders("ACTIVE", "小李", "高新区")).willReturn(List.of(
            new DispatchManagedRiderResponse(
                1L,
                "骑手小李",
                "李师傅",
                "13800000009",
                "ACTIVE",
                "ACTIVE",
                "高新区",
                "老板",
                "2026-05-15T08:00:00",
                "2026-05-15T11:30:00",
                12,
                8,
                "rider_openid_01"
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/riders")
                .param("authStatus", "ACTIVE")
                .param("keyword", "小李")
                .param("areaCode", "高新区"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].riderName").value("骑手小李"))
            .andExpect(jsonPath("$.data[0].todayTaskCount").value(12))
            .andExpect(jsonPath("$.data[0].currentOpenid").value("rider_openid_01"));
    }

    @Test
    void shouldCreateRider() throws Exception {
        given(dispatchService.createRider("骑手小周", "骑手小周", "13800000010", "高新区", "ACTIVE", "运营A"))
            .willReturn(new DispatchRiderProfileUpsertResponse(10L, "骑手小周", "骑手小周", "13800000010", "高新区", "ACTIVE"));

        mockMvc.perform(post("/api/admin/dispatch/riders")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小周",
                      "displayName": "骑手小周",
                      "phone": "13800000010",
                      "areaCode": "高新区",
                      "employmentStatus": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(10L))
            .andExpect(jsonPath("$.data.riderName").value("骑手小周"))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"));

        then(dispatchService).should().createRider("骑手小周", "骑手小周", "13800000010", "高新区", "ACTIVE", "运营A");
    }

    @Test
    void shouldRejectRepeatedDispatchReassignSubmission() throws Exception {
        given(dispatchService.reassignDispatch(
            "RIDER",
            1L,
            "王师傅",
            "李师傅",
            "高新区",
            "2026-05-15",
            "LUNCH",
            true,
            "运力调整",
            "运营A"
        )).willReturn(new DispatchReassignResultResponse("RIDER", 1L, "李师傅", "高新区", true, 2));

        String body = """
            {
              "reassignLevel": "RIDER",
              "targetId": 1,
              "fromRiderName": "王师傅",
              "toRiderName": "李师傅",
              "toAreaCode": "高新区",
              "serveDate": "2026-05-15",
              "mealPeriod": "LUNCH",
              "syncDefaultBinding": true,
              "reason": "运力调整"
            }
            """;

        mockMvc.perform(post("/api/admin/dispatch/reassign")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/dispatch/reassign")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REPEAT_SUBMISSION"));

        then(dispatchService).should(times(1)).reassignDispatch(
            "RIDER",
            1L,
            "王师傅",
            "李师傅",
            "高新区",
            "2026-05-15",
            "LUNCH",
            true,
            "运力调整",
            "运营A"
        );
    }

    @Test
    void shouldUpdateRiderProfile() throws Exception {
        given(dispatchService.updateRiderProfile(10L, "骑手小周已修改", "骑手小周已修改", "13800000011", "商务区", "运营A"))
            .willReturn(new DispatchRiderProfileUpsertResponse(10L, "骑手小周已修改", "骑手小周已修改", "13800000011", "商务区", "ACTIVE"));

        mockMvc.perform(post("/api/admin/dispatch/riders/10/profile")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小周已修改",
                      "displayName": "骑手小周已修改",
                      "phone": "13800000011",
                      "areaCode": "商务区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(10L))
            .andExpect(jsonPath("$.data.riderName").value("骑手小周已修改"))
            .andExpect(jsonPath("$.data.phone").value("13800000011"))
            .andExpect(jsonPath("$.data.areaCode").value("商务区"));

        then(dispatchService).should().updateRiderProfile(10L, "骑手小周已修改", "骑手小周已修改", "13800000011", "商务区", "运营A");
    }

    @Test
    void shouldReturnRiderProgressCards() throws Exception {
        given(dispatchService.riderProgress("LUNCH", "2026-05-15")).willReturn(List.of(
            new DispatchRiderProgressResponse(
                "骑手小李",
                "高新区",
                4,
                9,
                101L,
                5,
                102L,
                4,
                1
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/riders/progress")
                .param("mealPeriod", "LUNCH")
                .param("serveDate", "2026-05-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].riderName").value("骑手小李"))
            .andExpect(jsonPath("$.data[0].currentOrderId").value(101L))
            .andExpect(jsonPath("$.data[0].currentSequenceNumber").value(5))
            .andExpect(jsonPath("$.data[0].nextOrderId").value(102L))
            .andExpect(jsonPath("$.data[0].exceptionCount").value(1));
    }

    @Test
    void shouldReturnAreaBindingsWithReferenceAndReceiptImages() throws Exception {
        given(dispatchService.areaBindings("LUNCH", "2026-05-15")).willReturn(List.of(
            new DispatchAreaBindingResponse(
                "高新区",
                "高新区,软件园",
                1L,
                "骑手小李",
                "骑手小李",
                1,
                false,
                List.of(
                    new DispatchAreaOrderItemResponse(
                        101L,
                        5,
                        "张先生",
                        "13800000001",
                        "高新区软件园A座",
                        "DISPATCHING",
                        "骑手小李",
                        "少饭",
                        "优先配送",
                        true,
                        List.of("USER_NOTE"),
                        "需留意",
                        "https://img.example.com/reference-101.jpg",
                        "https://img.example.com/receipt-101.jpg",
                        "已放前台",
                        "2026-05-15 12:00:00",
                        1
                    )
                ),
                "老板",
                "2026-05-15 11:00:00"
            )
        ));

        mockMvc.perform(get("/api/admin/dispatch/area-bindings")
                .param("mealPeriod", "LUNCH")
                .param("serveDate", "2026-05-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].areaCode").value("高新区"))
            .andExpect(jsonPath("$.data[0].orders[0].referenceImageUrl").value("https://img.example.com/reference-101.jpg"))
            .andExpect(jsonPath("$.data[0].orders[0].receiptUrl").value("https://img.example.com/receipt-101.jpg"));
    }

    @Test
    void shouldReturnRiderAuthBinding() throws Exception {
        given(dispatchService.riderAuthBinding(1L)).willReturn(new DispatchRiderAuthBindingResponse(
            1L,
            "骑手小李",
            "李师傅",
            "13800000009",
            "rider_openid_01",
            "ACTIVE",
            "2026-05-15T11:30:00"
        ));

        mockMvc.perform(get("/api/admin/dispatch/riders/1/auth-binding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.displayName").value("李师傅"))
            .andExpect(jsonPath("$.data.currentOpenid").value("rider_openid_01"));
    }

    @Test
    void shouldTakeoverRiderAuthBinding() throws Exception {
        given(dispatchService.takeoverRiderAuth(1L, 9L, "运营A"))
            .willReturn(new DispatchRiderAuthTakeoverResponse(1L, 9L, "rider_openid_09", "ACTIVE"));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/takeover-auth")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceRiderId": 9
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.sourceRiderId").value(9L))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"));

        then(dispatchService).should().takeoverRiderAuth(1L, 9L, "运营A");
    }

    @Test
    void shouldUnbindRiderAuthBinding() throws Exception {
        given(dispatchService.unbindRiderAuth(1L, "运营A"))
            .willReturn(new DispatchRiderAuthUnbindResponse(1L, "", "DISABLED"));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/unbind-auth")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.riderStatus").value("DISABLED"));

        then(dispatchService).should().unbindRiderAuth(1L, "运营A");
    }

    @Test
    void shouldUpdateAreaBinding() throws Exception {
        given(dispatchService.updateAreaBinding("高新区", null, 1L, 2L, "运营A"))
            .willReturn(new DispatchAreaBindingUpdateResultResponse("高新区", null, 1L, 2L, "UPDATED"));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/高新区")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "defaultRiderId": 1,
                      "backupRiderId": 2
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.defaultRiderId").value(1L))
            .andExpect(jsonPath("$.data.backupRiderId").value(2L));

        then(dispatchService).should().updateAreaBinding("高新区", null, 1L, 2L, "运营A");
    }

    @Test
    void shouldCreateAreaBindingForNewArea() throws Exception {
        given(dispatchService.updateAreaBinding("万达商圈", "万达商圈", 1L, null, "运营A"))
            .willReturn(new DispatchAreaBindingUpdateResultResponse("万达商圈", "万达商圈", 1L, null, "CREATED"));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/万达商圈")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "keywords": "万达商圈",
                      "defaultRiderId": 1,
                      "backupRiderId": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("万达商圈"))
            .andExpect(jsonPath("$.data.keywords").value("万达商圈"))
            .andExpect(jsonPath("$.data.defaultRiderId").value(1L))
            .andExpect(jsonPath("$.data.status").value("CREATED"));

        then(dispatchService).should().updateAreaBinding("万达商圈", "万达商圈", 1L, null, "运营A");
    }

    @Test
    void shouldDisableRider() throws Exception {
        given(dispatchService.disableRider(1L, "运营A"))
            .willReturn(new DispatchRiderStatusResponse(1L, "DISABLED"));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/disable")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.riderStatus").value("DISABLED"));

        then(dispatchService).should().disableRider(1L, "运营A");
    }

    @Test
    void shouldAutoAssignPendingOrders() throws Exception {
        given(dispatchService.autoAssignPendingOrders())
            .willReturn(new DispatchAutoAssignResponse(3, 0));

        mockMvc.perform(post("/api/admin/dispatch/auto-assign"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignedCount").value(3))
            .andExpect(jsonPath("$.data.exceptionCount").value(0));
    }

    @Test
    void shouldConfirmExceptionArea() throws Exception {
        given(dispatchService.confirmExceptionArea(22L, "高新区", "王师傅", true, "运营A"))
            .willReturn(new DispatchExceptionAreaConfirmResponse(22L, "高新区", "王师傅", true, "运营A", "CONFIRMED"));

        mockMvc.perform(post("/api/admin/dispatch/exceptions/22/confirm-area")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaCode": "高新区",
                      "riderName": "王师傅",
                      "rememberAddress": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mealSlotOrderId").value(22L))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.riderName").value("王师傅"))
            .andExpect(jsonPath("$.data.rememberAddress").value(true))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        then(dispatchService).should().confirmExceptionArea(22L, "高新区", "王师傅", true, "运营A");
    }

    @Test
    void shouldNotifyCustomer() throws Exception {
        given(dispatchService.notifyCustomer(66L))
            .willReturn(new DispatchNotificationResponse(66L, "SKIPPED"));

        mockMvc.perform(post("/api/admin/dispatch/66/notify"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dispatchId").value(66L))
            .andExpect(jsonPath("$.data.notificationStatus").value("SKIPPED"));
    }

    @Test
    void shouldAssignRiderToAreaWithMealPeriodFilter() throws Exception {
        given(dispatchService.assignRiderToArea("高新区", "骑手小李", "运营A", "LUNCH"))
            .willReturn(new DispatchAreaRiderAssignResponse("高新区", 2, "LUNCH"));

        mockMvc.perform(post("/api/admin/dispatch/areas/高新区/assign-rider")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .param("mealPeriod", "LUNCH")
                .content("""
                    {
                      "riderName": "骑手小李"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.assignedCount").value(2));

        then(dispatchService).should().assignRiderToArea("高新区", "骑手小李", "运营A", "LUNCH");
    }

    @Test
    void shouldRemoveAreaBindingWithJsonBody() throws Exception {
        given(dispatchService.removeAreaBinding("高新区", 1L))
            .willReturn(new DispatchAreaBindingRemoveResponse("高新区", 1L, "REMOVED"));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/remove-rider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaCode": "高新区",
                      "riderId": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.status").value("REMOVED"));
    }

    @Test
    void shouldDeleteAreaWithJsonBody() throws Exception {
        given(dispatchService.deleteArea("高新区"))
            .willReturn(new DispatchAreaDeleteResponse("高新区", "DELETED"));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaCode": "高新区"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    @Test
    void shouldReturnStructuredErrorWhenDeleteAreaBlocked() throws Exception {
        given(dispatchService.deleteArea("高新区")).willThrow(new BusinessException(
            ErrorCode.DISPATCH_AREA_HAS_ACTIVE_ORDERS,
            "区域“高新区”还有 3 个配送中的订单，暂不能删除",
            Map.of(
                "areaCode", "高新区",
                "activeOrderCount", 3,
                "orders", List.of(
                    Map.of(
                        "orderId", 101L,
                        "customerName", "张先生",
                        "deliveryAddress", "高新区科技园A座8层",
                        "deliveryStatus", "DISPATCHING"
                    )
                )
            )
        ));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "areaCode": "高新区"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DISPATCH_AREA_HAS_ACTIVE_ORDERS"))
            .andExpect(jsonPath("$.message", containsString("暂不能删除")))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.activeOrderCount").value(3))
            .andExpect(jsonPath("$.data.orders[0].orderId").value(101L))
            .andExpect(jsonPath("$.data.orders[0].customerName").value("张先生"));
    }

    @Test
    void shouldReassignDispatchForCurrentDay() throws Exception {
        given(dispatchService.reassignDispatch(
            "AREA",
            101L,
            "骑手老王",
            "骑手小李",
            "高新区",
            "2026-05-15",
            "LUNCH",
            true,
            "老王请假",
            "运营A"
        )).willReturn(new DispatchReassignResultResponse(
            "AREA",
            101L,
            "骑手小李",
            "高新区",
            true,
            1
        ));

        mockMvc.perform(post("/api/admin/dispatch/reassign")
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reassignLevel": "AREA",
                      "targetId": 101,
                      "fromRiderName": "骑手老王",
                      "toRiderName": "骑手小李",
                      "toAreaCode": "高新区",
                      "serveDate": "2026-05-15",
                      "mealPeriod": "LUNCH",
                      "syncDefaultBinding": true,
                      "reason": "老王请假"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reassignLevel").value("AREA"))
            .andExpect(jsonPath("$.data.targetId").value(101L))
            .andExpect(jsonPath("$.data.toRiderName").value("骑手小李"))
            .andExpect(jsonPath("$.data.toAreaCode").value("高新区"))
            .andExpect(jsonPath("$.data.syncDefaultBinding").value(true));

        then(dispatchService).should().reassignDispatch(
            "AREA",
            101L,
            "骑手老王",
            "骑手小李",
            "高新区",
            "2026-05-15",
            "LUNCH",
            true,
            "老王请假",
            "运营A"
        );
    }
}
