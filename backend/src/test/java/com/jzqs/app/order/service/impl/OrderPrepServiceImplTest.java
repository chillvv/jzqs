package com.jzqs.app.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.OrderSpecialDispatchResponse;
import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.service.OrderDispatchService;
import com.jzqs.app.order.service.OrderOperationService;
import com.jzqs.app.order.service.OrderQueryService;
import com.jzqs.app.order.service.OrderSubscriptionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderPrepServiceImplTest {

    @Test
    void shouldDelegateQueryMethodsToOrderQueryService() {
        OrderQueryService queryService = Mockito.mock(OrderQueryService.class);
        OrderOperationService operationService = Mockito.mock(OrderOperationService.class);
        OrderSubscriptionService subscriptionService = Mockito.mock(OrderSubscriptionService.class);
        OrderDispatchService dispatchService = Mockito.mock(OrderDispatchService.class);
        OrderPrepServiceImpl facade = new OrderPrepServiceImpl(queryService, operationService, subscriptionService, dispatchService);

        OrderPrepStatsResponse stats = new OrderPrepStatsResponse(1, 1, 0, 1, 0, 0, 0, 0);
        PageResponse<OrderPrepItemResponse> page = PageResponse.of(List.of(), 1, 20, 0);
        List<ManualCreateCustomerSearchResponse> customers = List.of();
        OrderNotesResponse notes = new OrderNotesResponse(List.of(), List.of());

        when(queryService.prepStats("2026-06-26")).thenReturn(stats);
        when(queryService.prepPage("2026-06-26")).thenReturn(page);
        when(queryService.searchManualCreateCustomers("138")).thenReturn(customers);
        when(queryService.orderNotes(9L)).thenReturn(notes);

        assertSame(stats, facade.prepStats("2026-06-26"));
        assertSame(page, facade.prepPage("2026-06-26"));
        assertSame(customers, facade.searchManualCreateCustomers("138"));
        assertSame(notes, facade.orderNotes(9L));

        verify(queryService).prepStats("2026-06-26");
        verify(queryService).prepPage("2026-06-26");
        verify(queryService).searchManualCreateCustomers("138");
        verify(queryService).orderNotes(9L);
    }

    @Test
    void shouldDelegateOperationMethodsToOrderOperationService() {
        OrderQueryService queryService = Mockito.mock(OrderQueryService.class);
        OrderOperationService operationService = Mockito.mock(OrderOperationService.class);
        OrderSubscriptionService subscriptionService = Mockito.mock(OrderSubscriptionService.class);
        OrderDispatchService dispatchService = Mockito.mock(OrderDispatchService.class);
        OrderPrepServiceImpl facade = new OrderPrepServiceImpl(queryService, operationService, subscriptionService, dispatchService);

        OrderMerchantRemarkUpdateRequest remarkRequest = new OrderMerchantRemarkUpdateRequest("商家备注");
        OrderMerchantRemarkUpdateResponse remarkResponse = new OrderMerchantRemarkUpdateResponse(7L, "UPDATED");
        ManualCreateOrderResponse createResponse = new ManualCreateOrderResponse(8L, "PENDING_DISPATCH");
        OrderActionResponse cancelResponse = new OrderActionResponse(8L, "CANCELLED");
        BatchOperationResponse consumeResponse = new BatchOperationResponse(1, 0, List.of());

        when(operationService.updateMerchantRemark(7L, remarkRequest)).thenReturn(remarkResponse);
        when(operationService.manualCreate(1L, 2L, "LUNCH", "LUNCH", "备注", "软件园", "BACKEND", 1, "2026-06-26"))
            .thenReturn(createResponse);
        when(operationService.cancelOrder(8L)).thenReturn(cancelResponse);
        when(operationService.consumeOrders(List.of(8L))).thenReturn(consumeResponse);

        assertSame(remarkResponse, facade.updateMerchantRemark(7L, remarkRequest));
        assertSame(createResponse, facade.manualCreate(1L, 2L, "LUNCH", "LUNCH", "备注", "软件园", "BACKEND", 1, "2026-06-26"));
        assertSame(cancelResponse, facade.cancelOrder(8L));
        assertSame(consumeResponse, facade.consumeOrders(List.of(8L)));

        verify(operationService).updateMerchantRemark(7L, remarkRequest);
        verify(operationService).manualCreate(1L, 2L, "LUNCH", "LUNCH", "备注", "软件园", "BACKEND", 1, "2026-06-26");
        verify(operationService).cancelOrder(8L);
        verify(operationService).consumeOrders(List.of(8L));
    }

    @Test
    void shouldDelegateSubscriptionAndDispatchMethodsToDedicatedServices() {
        OrderQueryService queryService = Mockito.mock(OrderQueryService.class);
        OrderOperationService operationService = Mockito.mock(OrderOperationService.class);
        OrderSubscriptionService subscriptionService = Mockito.mock(OrderSubscriptionService.class);
        OrderDispatchService dispatchService = Mockito.mock(OrderDispatchService.class);
        OrderPrepServiceImpl facade = new OrderPrepServiceImpl(queryService, operationService, subscriptionService, dispatchService);

        SubscriptionActionResponse confirmResponse = new SubscriptionActionResponse(11L, "CONFIRMED");
        SubscriptionActionResponse cancelResponse = new SubscriptionActionResponse(11L, "CANCELLED");
        SubscriptionBulkImportResponse importResponse = new SubscriptionBulkImportResponse(1, 0, List.of());
        OrderSpecialDispatchResponse specialDispatchResponse = new OrderSpecialDispatchResponse(9L, "UPDATED", "DINNER");
        DeliveryReceiptDeleteResponse deleteReceiptResponse = new DeliveryReceiptDeleteResponse(9L, "DISPATCHING", "", true);

        when(subscriptionService.confirmSubscription(11L)).thenReturn(confirmResponse);
        when(subscriptionService.cancelSubscription(11L, "临时停餐")).thenReturn(cancelResponse);
        when(subscriptionService.bulkImportSubscription(anyString(), any())).thenReturn(importResponse);
        when(dispatchService.updateSpecialDispatch(9L, "DINNER")).thenReturn(specialDispatchResponse);
        when(dispatchService.deleteDeliveryReceipt(9L)).thenReturn(deleteReceiptResponse);

        assertSame(confirmResponse, facade.confirmSubscription(11L));
        assertSame(cancelResponse, facade.cancelSubscription(11L, "临时停餐"));
        assertSame(importResponse, facade.bulkImportSubscription("2026-06-26", List.of()));
        assertSame(specialDispatchResponse, facade.updateSpecialDispatch(9L, "DINNER"));
        assertSame(deleteReceiptResponse, facade.deleteDeliveryReceipt(9L));

        verify(subscriptionService).confirmSubscription(11L);
        verify(subscriptionService).cancelSubscription(11L, "临时停餐");
        verify(subscriptionService).bulkImportSubscription("2026-06-26", List.of());
        verify(dispatchService).updateSpecialDispatch(9L, "DINNER");
        verify(dispatchService).deleteDeliveryReceipt(9L);
    }
}
