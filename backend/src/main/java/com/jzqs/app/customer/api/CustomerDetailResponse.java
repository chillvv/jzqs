package com.jzqs.app.customer.api;

import java.util.List;

public record CustomerDetailResponse(
    long id,
    String name,
    String phone,
    String customerStatus,
    String merchantRemark,
    boolean priorityCustomer,
    String priorityTag,
    String priorityNote,
    int remainingMeals,
    String openedAt,
    String expiredAt,
    Integer remainingValidityDays,
    String registeredAt,
    String lastOrderAt,
    CustomerWalletDetailResponse wallet,
    List<CustomerAddressDetailResponse> addresses,
    List<CustomerSubscriptionDetailResponse> subscriptions,
    List<WalletTransactionResponse> transactions
) {
}
