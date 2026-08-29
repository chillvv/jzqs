package com.jzqs.app.order.service;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderNoteSnapshotService {
    /**
     * 重建某订单的备注快照（全删重写）。
     *
     * @param orderUserNote         用户本单输入的用户备注（可空）
     * @param orderOnceMerchantNotes 本单一次性商家备注（可空，会与库上既有的
     *                               {@code MERCHANT_ORDER_ONCE} 行合并去重）
     */
    void writeOrderSnapshot(
        long mealSlotOrderId,
        long customerId,
        String operatorName,
        String orderUserNote,
        List<String> orderOnceMerchantNotes,
        LocalDateTime snapshotTime
    );
}
