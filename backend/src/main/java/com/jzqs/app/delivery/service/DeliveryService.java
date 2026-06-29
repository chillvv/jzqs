package com.jzqs.app.delivery.service;

import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DeliveryService {
    DeliveryReceiptUploadResponse uploadReceiptImage(MultipartFile file);

    DeliveryReceiptRecordResponse recordDeliveryReceipt(
        long orderId,
        String receiptUrl,
        String receiptNote,
        String deliveredAt,
        String visibleAt,
        String expiresAt
    );
}
