package com.jzqs.app.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class DailyOrderTest {
    @Test
    void shouldAddLunchAndDinnerSlotsForSameDay() {
        DailyOrder order = DailyOrder.create(1L, LocalDate.of(2026, 5, 13), "BACKEND");
        order.addSlot(MealPeriod.LUNCH, 2, 10L, "少饭");
        order.addSlot(MealPeriod.DINNER, 1, 10L, "");
        assertEquals(2, order.slotCount());
        assertEquals(3, order.totalPortions());
    }
    @Test
    void shouldRejectDuplicateMealPeriod() {
        DailyOrder order = DailyOrder.create(1L, LocalDate.of(2026, 5, 13), "BACKEND");
        order.addSlot(MealPeriod.LUNCH, 1, 10L, "");
        assertThrows(IllegalStateException.class, () -> order.addSlot(MealPeriod.LUNCH, 1, 10L, ""));
    }
}
