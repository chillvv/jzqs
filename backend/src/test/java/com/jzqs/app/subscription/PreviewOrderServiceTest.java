package com.jzqs.app.subscription;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jzqs.app.order.DailyOrder;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
class PreviewOrderServiceTest {
    @Test
    void shouldGeneratePreviewOrdersForActiveSubscribers() {
        SubscriptionRule rule = SubscriptionRule.of(1L, true, 1, false, 0);
        PreviewOrderService service = new PreviewOrderService();
        List<DailyOrder> orders = service.generatePreviewOrders(List.of(rule), LocalDate.of(2026, 5, 12), 100L);
        assertEquals(1, orders.size());
        assertEquals(1, orders.get(0).totalPortions());
    }
}
