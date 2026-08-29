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

    /**
     * 重建某订单的备注快照（全删重写）。
     *
     * <p>条目顺序：客户档案用户备注 → 本单用户备注 → 本单一次性商家备注 → 客户档案商家备注。
     * 用户侧与商家侧靠 {@code note_type} 分开，展示时各自成栏、栏内逗号拼接。
     *
     * <p>调用方必须把该订单当前的「订单级商家备注」通过 {@code orderOnceMerchantNotes} 传进来，
     * 否则这一路备注不会出现在快照里（历史 bug：所有调用方都传空，导致订单上的商家备注在
     * 用户备注出现后被整列丢弃）。
     */
    @Override
    public void writeOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    ) {
        LocalDateTime effectiveSnapshotTime = snapshotTime == null ? LocalDateTime.now() : snapshotTime;
        String createdBy = normalizeOptionalText(operatorName);

        LinkedHashSet<String> merchantOrderNotes = new LinkedHashSet<>();
        if (orderOnceMerchantNotes != null) {
            for (String merchantOrderNote : orderOnceMerchantNotes) {
                String normalized = normalizeOptionalText(merchantOrderNote);
                if (normalized != null) {
                    merchantOrderNotes.add(normalized);
                }
            }
        }

        orderNoteSnapshotRepository.deleteSnapshots(mealSlotOrderId);

        List<OrderNoteSnapshotRepository.SnapshotInsert> inserts = new ArrayList<>();
        inserts.addAll(orderNoteSnapshotRepository.loadCustomerUserNotes(customerId));

        String normalizedOrderUserNote = normalizeOptionalText(orderUserNote);
        if (normalizedOrderUserNote != null) {
            inserts.add(new OrderNoteSnapshotRepository.SnapshotInsert("USER", "CUSTOMER_ORDER_INPUT", "SNAPSHOT", normalizedOrderUserNote));
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
