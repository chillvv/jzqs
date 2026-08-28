package com.jzqs.app.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.persistence.OrderSubscriptionRepository;
import com.jzqs.app.order.service.OrderSubscriptionImportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderSubscriptionServiceImplTest {

    @Test
    void shouldReturnFailureWhenCustomerHasNoRemainingMeals() {
        OrderSubscriptionRepository repository = Mockito.mock(OrderSubscriptionRepository.class);
        OrderSubscriptionImportService executor = Mockito.mock(OrderSubscriptionImportService.class);
        OrderSubscriptionServiceImpl service = new OrderSubscriptionServiceImpl(repository, executor);
        SubscriptionImportItem item = new SubscriptionImportItem(9L, "LUNCH", "LUNCH", 12L, "少辣");

        when(repository.findRemainingMeals(9L)).thenReturn(0);
        when(repository.findCustomerName(9L)).thenReturn("张三");

        SubscriptionBulkImportResponse response = service.bulkImportSubscription("2026-06-26", List.of(item));

        assertEquals(0, response.successCount());
        assertEquals(1, response.failureCount());
        assertEquals("张三", response.failures().get(0).customerName());
        assertEquals("余额不足或未找到可用套餐", response.failures().get(0).reason());
        verify(executor, never()).importSingleItem(item, "2026-06-26", "软件园");
    }

    @Test
    void shouldContinueWhenSingleImportFails() {
        OrderSubscriptionRepository repository = Mockito.mock(OrderSubscriptionRepository.class);
        OrderSubscriptionImportService executor = Mockito.mock(OrderSubscriptionImportService.class);
        OrderSubscriptionServiceImpl service = new OrderSubscriptionServiceImpl(repository, executor);
        SubscriptionImportItem item = new SubscriptionImportItem(9L, "DINNER", "DINNER", 12L, "不要香菜");

        when(repository.findRemainingMeals(9L)).thenReturn(2);
        when(repository.findCustomerName(9L)).thenReturn("李四");
        when(repository.findAddressLine(12L)).thenReturn("软件园一路");
        doThrow(new IllegalStateException("导入失败")).when(executor).importSingleItem(item, "2026-06-26", "软件园一路");

        SubscriptionBulkImportResponse response = service.bulkImportSubscription("2026-06-26", List.of(item));

        assertEquals(0, response.successCount());
        assertEquals(1, response.failureCount());
        assertEquals("导入失败", response.failures().get(0).reason());
        verify(executor).importSingleItem(item, "2026-06-26", "软件园一路");
    }

    @Test
    void shouldReturnFailureWhenAddressDoesNotExist() {
        OrderSubscriptionRepository repository = Mockito.mock(OrderSubscriptionRepository.class);
        OrderSubscriptionImportService executor = Mockito.mock(OrderSubscriptionImportService.class);
        OrderSubscriptionServiceImpl service = new OrderSubscriptionServiceImpl(repository, executor);
        SubscriptionImportItem item = new SubscriptionImportItem(9L, "DINNER", "DINNER", 12L, "不要香菜");

        when(repository.findRemainingMeals(9L)).thenReturn(2);
        when(repository.findCustomerName(9L)).thenReturn("李四");
        when(repository.findAddressLine(12L)).thenReturn(null);

        SubscriptionBulkImportResponse response = service.bulkImportSubscription("2026-06-26", List.of(item));

        assertEquals(0, response.successCount());
        assertEquals(1, response.failureCount());
        assertEquals("配送地址不存在", response.failures().get(0).reason());
        verify(executor, never()).importSingleItem(item, "2026-06-26", "软件园一路");
    }

    @Test
    void shouldThrowWhenConfirmationDoesNotExist() {
        OrderSubscriptionRepository repository = Mockito.mock(OrderSubscriptionRepository.class);
        OrderSubscriptionImportService executor = Mockito.mock(OrderSubscriptionImportService.class);
        OrderSubscriptionServiceImpl service = new OrderSubscriptionServiceImpl(repository, executor);

        when(repository.confirmSubscription(7L, "后台客服")).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirmSubscription(7L));
    }
}
