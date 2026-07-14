package com.jzqs.app.customer.api;

public record WalletTransactionResponse(
    long id,
    long customerId,
    String transactionType,
    int mealDelta,
    String operatorName,
    String remark,
    Long relatedOrderId,
    Long relatedAftersaleId,
    Long relatedTransactionId,
    boolean refunded,
    String refundReasonCode,
    String refundReasonText,
    String createdAt
) {
}
