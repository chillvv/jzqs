package com.jzqs.app.subscription;
public record SubscriptionRule(
    long customerId,
    boolean lunchEnabled,
    int lunchQuantity,
    boolean dinnerEnabled,
    int dinnerQuantity
) {
    public static SubscriptionRule of(long customerId, boolean lunchEnabled, int lunchQuantity, boolean dinnerEnabled, int dinnerQuantity) {
        return new SubscriptionRule(customerId, lunchEnabled, lunchQuantity, dinnerEnabled, dinnerQuantity);
    }
}
