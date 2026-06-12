package com.jzqs.app.dispatch.api;

import java.util.List;

public record DispatchAreaOrderItemResponse(
    long orderId,
    int sequenceNumber,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String deliveryStatus,
    String riderName,
    String userNote,
    String merchantRemark,
    boolean hasAttentionMark,
    List<String> attentionSources,
    String attentionLabel,
    String referenceImageUrl,
    String receiptUrl,
    String receiptNote,
    String deliveredAt,
    int quantity
) {
}
