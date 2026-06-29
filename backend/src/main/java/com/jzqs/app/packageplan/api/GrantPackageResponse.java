package com.jzqs.app.packageplan.api;

public record GrantPackageResponse(
    long customerId,
    String packageCode,
    int remainingMeals
) {}
