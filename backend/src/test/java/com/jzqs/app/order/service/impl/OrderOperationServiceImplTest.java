package com.jzqs.app.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.persistence.OrderOperationRepository;
import com.jzqs.app.order.persistence.OrderSupportRepository;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderOperationServiceImplTest {

    @Test
    void shouldUseRepositoryToUpdateMerchantRemark() {
        OrderSupportRepository supportRepository = Mockito.mock(OrderSupportRepository.class);
        OrderOperationRepository repository = Mockito.mock(OrderOperationRepository.class);
        OrderNoteSnapshotService snapshotService = Mockito.mock(OrderNoteSnapshotService.class);
        OrderOperationServiceImpl service = new OrderOperationServiceImpl(supportRepository, repository, snapshotService, Mockito.mock(RealtimeAudienceModule.class));

        when(repository.updateMerchantRemark(7L, "少辣")).thenReturn(1);

        OrderMerchantRemarkUpdateResponse response = service.updateMerchantRemark(7L, new OrderMerchantRemarkUpdateRequest("少辣"));

        assertEquals(7L, response.orderId());
        assertEquals("UPDATED", response.status());
    }

    @Test
    void shouldThrowWhenUpdatingMissingMerchantRemarkOrder() {
        OrderSupportRepository supportRepository = Mockito.mock(OrderSupportRepository.class);
        OrderOperationRepository repository = Mockito.mock(OrderOperationRepository.class);
        OrderNoteSnapshotService snapshotService = Mockito.mock(OrderNoteSnapshotService.class);
        OrderOperationServiceImpl service = new OrderOperationServiceImpl(supportRepository, repository, snapshotService, Mockito.mock(RealtimeAudienceModule.class));

        when(repository.updateMerchantRemark(7L, "少辣")).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.updateMerchantRemark(7L, new OrderMerchantRemarkUpdateRequest("少辣")));
    }
}
