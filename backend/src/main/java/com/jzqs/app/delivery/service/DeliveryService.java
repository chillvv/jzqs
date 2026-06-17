package com.jzqs.app.delivery.service;

import com.jzqs.app.delivery.api.DeliveryReceiptUploadResponse;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface DeliveryService {
    DeliveryReceiptUploadResponse uploadReceiptImage(MultipartFile file);

    Map<String, Object> recordDeliveryReceipt(
        long orderId,
        String receiptUrl,
        String receiptNote,
        String deliveredAt,
        String visibleAt,
        String expiresAt
    );
}
