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
import java.util.List;
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
        when(repository.findOrderCustomerIds(7L)).thenReturn(List.of(31L));
        when(repository.findOrderUserNote(7L)).thenReturn("少饭");

        OrderMerchantRemarkUpdateResponse response = service.updateMerchantRemark(7L, new OrderMerchantRemarkUpdateRequest("少辣"));

        assertEquals(7L, response.orderId());
        assertEquals("UPDATED", response.status());
        // 订单列改了备注，快照必须跟着重建，否则三端还展示旧备注
        Mockito.verify(snapshotService).writeOrderSnapshot(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(31L),
            org.mockito.ArgumentMatchers.eq("后台客服"),
            org.mockito.ArgumentMatchers.eq("少饭"),
            org.mockito.ArgumentMatchers.eq(List.of("少辣")),
            org.mockito.ArgumentMatchers.any()
        );
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
