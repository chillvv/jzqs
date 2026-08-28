package com.jzqs.app.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.persistence.OrderQueryRepository;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderQueryServiceImplTest {

    @Test
    void shouldBuildPreviewCheckFromRepositoryPreview() {
        OrderQueryRepository repository = Mockito.mock(OrderQueryRepository.class);
        OrderQueryServiceImpl service = new OrderQueryServiceImpl(repository);

        when(repository.findSubscriptionPreview(LocalDate.parse("2026-06-26"))).thenReturn(List.of(
            new SubscriptionPreviewItem(1L, "张三", "13800138000", "LUNCH", "LUNCH", 2L, "软件园", "-", 0, false),
            new SubscriptionPreviewItem(2L, "李四", "13800138001", "DINNER", "DINNER", 3L, "科创路", "-", 2, true)
        ));

        SubscriptionPreviewCheckResponse response = service.subscriptionPreviewCheck("2026-06-26");

        assertEquals(2, response.totalCount());
        assertEquals(1, response.sufficientCount());
        assertEquals(1, response.insufficientCount());
        assertEquals("张三", response.insufficientCustomers().get(0).customerName());
    }

    @Test
    void shouldDelegatePrepStatsToRepository() {
        OrderQueryRepository repository = Mockito.mock(OrderQueryRepository.class);
        OrderQueryServiceImpl service = new OrderQueryServiceImpl(repository);
        OrderPrepStatsResponse stats = new OrderPrepStatsResponse(1, 2, 3, 4, 5, 6, 7, 8);

        when(repository.loadPrepStats(LocalDate.parse("2026-06-26"))).thenReturn(stats);

        assertSame(stats, service.prepStats("2026-06-26"));
    }
}
