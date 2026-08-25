package com.jzqs.app.delivery.api;
import jakarta.validation.constraints.Min;
public record DeliveryReceiptRequest(
    @Min(1) long mealSlotOrderId,
    // 允许为空：商家可以只填写回执说明而暂不上传送达图片。
    String receiptUrl,
    String receiptNote,
    // 允许为空：为空时后端按当前上传时间作为送达时间。
    String deliveredAt
) {
}
