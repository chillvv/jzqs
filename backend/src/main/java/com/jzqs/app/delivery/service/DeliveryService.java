package com.jzqs.app.delivery.service;

import java.util.Map;

public interface DeliveryService {
    Map<String, Object> recordDeliveryReceipt(
        long orderId,
        String receiptUrl,
        String receiptNote,
        String deliveredAt,
        String visibleAt,
        String expiresAt
    );
}
