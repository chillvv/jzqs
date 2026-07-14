package com.jzqs.app.subscription.api;

public record SubscriptionRuleTogglePauseResponse(
    long id,
    boolean paused
) {}
