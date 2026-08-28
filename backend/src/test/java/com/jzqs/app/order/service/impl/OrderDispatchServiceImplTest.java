package com.jzqs.app.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.persistence.OrderDispatchRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderDispatchServiceImplTest {

    @Test
    void shouldReturnNotFoundWhenDeletingReceiptForMissingOrder() {
        OrderDispatchRepository repository = Mockito.mock(OrderDispatchRepository.class);
        DispatchService dispatchService = Mockito.mock(DispatchService.class);
        OrderDispatchServiceImpl service = new OrderDispatchServiceImpl(repository, dispatchService);

        when(repository.findOrderStatus(99L)).thenReturn(Optional.empty());

        DeliveryReceiptDeleteResponse response = service.deleteDeliveryReceipt(99L);

        assertEquals("NOT_FOUND", response.orderStatus());
        assertEquals(false, response.deleted());
        verify(repository, never()).clearLatestDeliveryReceipt(1L);
    }

    @Test
    void shouldClearLatestReceiptWhenExists() {
        OrderDispatchRepository repository = Mockito.mock(OrderDispatchRepository.class);
        DispatchService dispatchService = Mockito.mock(DispatchService.class);
        OrderDispatchServiceImpl service = new OrderDispatchServiceImpl(repository, dispatchService);

        when(repository.findOrderStatus(8L)).thenReturn(Optional.of("DISPATCHING"));
        when(repository.findLatestDeliveryReceipt(8L))
            .thenReturn(Optional.of(new OrderDispatchRepository.DeliveryReceiptRecord(15L, "https://img")));

        DeliveryReceiptDeleteResponse response = service.deleteDeliveryReceipt(8L);

        assertEquals("DISPATCHING", response.orderStatus());
        assertEquals(true, response.deleted());
        verify(repository).clearLatestDeliveryReceipt(15L);
    }
}
