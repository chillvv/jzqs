package com.jzqs.app.order.service;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderNoteSnapshotService {
    void writeOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        String subscriptionDefaultNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    );
}
