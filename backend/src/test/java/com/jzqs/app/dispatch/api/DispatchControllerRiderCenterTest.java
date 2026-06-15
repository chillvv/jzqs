package com.jzqs.app.dispatch.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.service.DispatchService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DispatchController.class)
class DispatchControllerRiderCenterTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispatchService dispatchService;

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
            "老板"
        )).willReturn(new BatchOperationResponse(2, 0, List.of()));

        mockMvc.perform(post("/api/admin/dispatch/pending-items/batch-assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderIds": [101, 102],
                      "areaCode": "高新区",
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.successCount").value(2))
            .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    @Test
    void shouldReorderAreaOrders() throws Exception {
        given(dispatchService.reorderAreaOrders(
            "高新区",
            List.of(
                new DispatchOrderReorderItemRequest(101L, 1),
                new DispatchOrderReorderItemRequest(102L, 2)
            )
        )).willReturn(java.util.Map.of(
            "areaCode", "高新区",
            "updatedCount", 2
        ));

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
            "老板"
        )).willReturn(java.util.Map.of(
            "areaCode", "高新区",
            "orderId", 101L,
            "targetAreaCode", "商务区"
        ));

        mockMvc.perform(post("/api/admin/dispatch/areas/高新区/orders/101/move")
                .contentType(MediaType.APPLICATION_JSON)
                .param("mealPeriod", "LUNCH")
                .content("""
                    {
                      "targetAreaCode": "商务区",
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.orderId").value(101L))
            .andExpect(jsonPath("$.data.targetAreaCode").value("商务区"));
    }

    @Test
    void shouldResolveDispatchException() throws Exception {
        given(dispatchService.assignOrder(22L, "王师傅", "高新区")).willReturn(java.util.Map.of(
            "mealSlotOrderId", 22L,
            "riderName", "王师傅",
            "status", "DISPATCHED"
        ));

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
        given(dispatchService.activateRider(1L, "骑手小李", "高新区", "老板")).willReturn(Map.of(
            "riderId", 1L,
            "riderStatus", "ACTIVE",
            "areaCode", "高新区"
        ));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小李",
                      "areaCode": "高新区",
                      "assignedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"));
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
        given(dispatchService.createRider("骑手小周", "骑手小周", "13800000010", "高新区", "ACTIVE", "老板")).willReturn(Map.of(
            "riderId", 10L,
            "riderName", "骑手小周",
            "displayName", "骑手小周",
            "phone", "13800000010",
            "areaCode", "高新区",
            "riderStatus", "ACTIVE"
        ));

        mockMvc.perform(post("/api/admin/dispatch/riders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小周",
                      "displayName": "骑手小周",
                      "phone": "13800000010",
                      "areaCode": "高新区",
                      "employmentStatus": "ACTIVE",
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(10L))
            .andExpect(jsonPath("$.data.riderName").value("骑手小周"))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"));
    }

    @Test
    void shouldUpdateRiderProfile() throws Exception {
        given(dispatchService.updateRiderProfile(10L, "骑手小周已修改", "骑手小周已修改", "13800000011", "商务区", "老板")).willReturn(Map.of(
            "riderId", 10L,
            "riderName", "骑手小周已修改",
            "displayName", "骑手小周已修改",
            "phone", "13800000011",
            "areaCode", "商务区"
        ));

        mockMvc.perform(post("/api/admin/dispatch/riders/10/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "riderName": "骑手小周已修改",
                      "displayName": "骑手小周已修改",
                      "phone": "13800000011",
                      "areaCode": "商务区",
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(10L))
            .andExpect(jsonPath("$.data.riderName").value("骑手小周已修改"))
            .andExpect(jsonPath("$.data.phone").value("13800000011"))
            .andExpect(jsonPath("$.data.areaCode").value("商务区"));
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
        given(dispatchService.takeoverRiderAuth(1L, 9L, "老板")).willReturn(Map.of(
            "riderId", 1L,
            "sourceRiderId", 9L,
            "currentOpenid", "rider_openid_09",
            "riderStatus", "ACTIVE"
        ));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/takeover-auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceRiderId": 9,
                      "assignedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.sourceRiderId").value(9L))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"));
    }

    @Test
    void shouldUnbindRiderAuthBinding() throws Exception {
        given(dispatchService.unbindRiderAuth(1L, "老板")).willReturn(Map.of(
            "riderId", 1L,
            "currentOpenid", "",
            "riderStatus", "DISABLED"
        ));

        mockMvc.perform(post("/api/admin/dispatch/riders/1/unbind-auth")
                .param("assignedBy", "老板"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(1L))
            .andExpect(jsonPath("$.data.riderStatus").value("DISABLED"));
    }

    @Test
    void shouldUpdateAreaBinding() throws Exception {
        given(dispatchService.updateAreaBinding("高新区", null, 1L, 2L, "老板")).willReturn(Map.of(
            "areaCode", "高新区",
            "defaultRiderId", 1L,
            "backupRiderId", 2L
        ));

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/高新区")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "defaultRiderId": 1,
                      "backupRiderId": 2,
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.defaultRiderId").value(1L))
            .andExpect(jsonPath("$.data.backupRiderId").value(2L));
    }

    @Test
    void shouldCreateAreaBindingForNewArea() throws Exception {
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("areaCode", "万达商圈");
        response.put("keywords", "万达商圈");
        response.put("defaultRiderId", 1L);
        response.put("backupRiderId", null);
        response.put("status", "CREATED");
        given(dispatchService.updateAreaBinding("万达商圈", "万达商圈", 1L, null, "老板")).willReturn(response);

        mockMvc.perform(post("/api/admin/dispatch/area-bindings/万达商圈")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "keywords": "万达商圈",
                      "defaultRiderId": 1,
                      "backupRiderId": null,
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("万达商圈"))
            .andExpect(jsonPath("$.data.keywords").value("万达商圈"))
            .andExpect(jsonPath("$.data.defaultRiderId").value(1L))
            .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void shouldAssignRiderToAreaWithMealPeriodFilter() throws Exception {
        given(dispatchService.assignRiderToArea("高新区", "骑手小李", "老板", "LUNCH")).willReturn(Map.of(
            "areaCode", "高新区",
            "assignedCount", 2
        ));

        mockMvc.perform(post("/api/admin/dispatch/areas/高新区/assign-rider")
                .contentType(MediaType.APPLICATION_JSON)
                .param("mealPeriod", "LUNCH")
                .content("""
                    {
                      "riderName": "骑手小李",
                      "updatedBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("高新区"))
            .andExpect(jsonPath("$.data.assignedCount").value(2));
    }

    @Test
    void shouldRemoveAreaBindingWithJsonBody() throws Exception {
        given(dispatchService.removeAreaBinding("高新区", 1L)).willReturn(Map.of(
            "areaCode", "高新区",
            "riderId", 1L,
            "status", "REMOVED"
        ));

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
        given(dispatchService.deleteArea("高新区")).willReturn(Map.of(
            "areaCode", "高新区",
            "status", "DELETED"
        ));

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
            .andExpect(status().isBadRequest())
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
            "老板"
        )).willReturn(Map.of(
            "reassignLevel", "AREA",
            "targetId", 101L,
            "toRiderName", "骑手小李",
            "syncDefaultBinding", true
        ));

        mockMvc.perform(post("/api/admin/dispatch/reassign")
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
                      "reason": "老王请假",
                      "createdBy": "老板"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reassignLevel").value("AREA"))
            .andExpect(jsonPath("$.data.targetId").value(101L))
            .andExpect(jsonPath("$.data.toRiderName").value("骑手小李"))
            .andExpect(jsonPath("$.data.syncDefaultBinding").value(true));
    }
}
