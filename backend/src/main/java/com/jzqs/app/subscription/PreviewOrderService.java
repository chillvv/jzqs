package com.jzqs.app.subscription;
import com.jzqs.app.order.DailyOrder;
import com.jzqs.app.order.MealPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class PreviewOrderService {
    public List<DailyOrder> generatePreviewOrders(List<SubscriptionRule> rules, LocalDate today, long defaultAddressId) {
        LocalDate nextDay = today.plusDays(1);
        List<DailyOrder> result = new ArrayList<>();
        for (SubscriptionRule rule : rules) {
            DailyOrder order = DailyOrder.create(rule.customerId(), nextDay, "SUBSCRIPTION_PREVIEW");
            if (rule.lunchEnabled() && rule.lunchQuantity() > 0) {
                order.addSlot(MealPeriod.LUNCH, rule.lunchQuantity(), defaultAddressId, "");
            }
            if (rule.dinnerEnabled() && rule.dinnerQuantity() > 0) {
                order.addSlot(MealPeriod.DINNER, rule.dinnerQuantity(), defaultAddressId, "");
            }
            if (order.slotCount() > 0) {
                result.add(order);
            }
        }
        return result;
    }
}
