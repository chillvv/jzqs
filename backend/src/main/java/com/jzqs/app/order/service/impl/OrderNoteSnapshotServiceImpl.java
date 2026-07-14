package com.jzqs.app.order.service.impl;

import com.jzqs.app.order.persistence.OrderNoteSnapshotRepository;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderNoteSnapshotServiceImpl implements OrderNoteSnapshotService {
    private final OrderNoteSnapshotRepository orderNoteSnapshotRepository;

    public OrderNoteSnapshotServiceImpl(OrderNoteSnapshotRepository orderNoteSnapshotRepository) {
        this.orderNoteSnapshotRepository = orderNoteSnapshotRepository;
    }

    @Override
    public void writeOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        String subscriptionDefaultNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    ) {
        LocalDateTime effectiveSnapshotTime = snapshotTime == null ? LocalDateTime.now() : snapshotTime;
        String createdBy = normalizeOptionalText(operatorName);

        orderNoteSnapshotRepository.deleteSnapshots(mealSlotOrderId);

        List<OrderNoteSnapshotRepository.SnapshotInsert> inserts = new ArrayList<>();
        inserts.addAll(orderNoteSnapshotRepository.loadCustomerUserNotes(customerId));

        String normalizedOrderUserNote = normalizeOptionalText(orderUserNote);
        if (normalizedOrderUserNote != null) {
            inserts.add(new OrderNoteSnapshotRepository.SnapshotInsert("USER", "CUSTOMER_ORDER_INPUT", "SNAPSHOT", normalizedOrderUserNote));
        }

        String normalizedSubscriptionDefaultNote = normalizeOptionalText(subscriptionDefaultNote);
        if (normalizedSubscriptionDefaultNote != null) {
            inserts.add(new OrderNoteSnapshotRepository.SnapshotInsert("USER", "SUBSCRIPTION_DEFAULT", "SNAPSHOT", normalizedSubscriptionDefaultNote));
        }

        LinkedHashSet<String> merchantOrderNotes = new LinkedHashSet<>();
        if (orderOnceMerchantNotes != null) {
            for (String merchantOrderNote : orderOnceMerchantNotes) {
                String normalizedMerchantOrderNote = normalizeOptionalText(merchantOrderNote);
                if (normalizedMerchantOrderNote != null) {
                    merchantOrderNotes.add(normalizedMerchantOrderNote);
                }
            }
        }
        for (String merchantOrderNote : merchantOrderNotes) {
            inserts.add(new OrderNoteSnapshotRepository.SnapshotInsert("MERCHANT", "MERCHANT_ORDER_ONCE", "ORDER_ONCE", merchantOrderNote));
        }

        inserts.addAll(orderNoteSnapshotRepository.loadCustomerMerchantNotes(customerId, effectiveSnapshotTime));

        for (OrderNoteSnapshotRepository.SnapshotInsert insert : inserts) {
            orderNoteSnapshotRepository.insertSnapshot(mealSlotOrderId, customerId, insert, createdBy);
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }
}
