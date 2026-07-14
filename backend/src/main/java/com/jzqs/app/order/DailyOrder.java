package com.jzqs.app.order;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class DailyOrder {
    private final long customerId;
    private final LocalDate serveDate;
    private final String source;
    private final List<MealSlotOrder> slots = new ArrayList<>();
    private DailyOrder(long customerId, LocalDate serveDate, String source) {
        this.customerId = customerId;
        this.serveDate = serveDate;
        this.source = source;
    }
    public static DailyOrder create(long customerId, LocalDate serveDate, String source) {
        return new DailyOrder(customerId, serveDate, source);
    }
    public void addSlot(MealPeriod mealPeriod, int quantity, long addressId, String note) {
        boolean exists = slots.stream().anyMatch(slot -> slot.mealPeriod() == mealPeriod);
        if (exists) {
            throw new IllegalStateException("meal period already exists");
        }
        slots.add(new MealSlotOrder(mealPeriod, quantity, addressId, note, OrderStatus.PENDING_CONFIRMATION));
    }
    public int slotCount() {
        return slots.size();
    }
    public int totalPortions() {
        return slots.stream().mapToInt(MealSlotOrder::quantity).sum();
    }
}
