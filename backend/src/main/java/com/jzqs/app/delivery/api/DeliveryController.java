package com.jzqs.app.delivery.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/admin/deliveries")
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/receipt")
    public ApiResponse<Map<String, Object>> receipt(@Valid @RequestBody DeliveryReceiptRequest request) {
        return ApiResponse.success(deliveryService.recordDeliveryReceipt(
            request.mealSlotOrderId(),
            request.receiptUrl(),
            request.receiptNote() == null ? "" : request.receiptNote(),
            request.deliveredAt(),
            null,
            null
        ));
    }

    @PostMapping(value = "/receipt/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DeliveryReceiptUploadResponse> uploadReceipt(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(deliveryService.uploadReceiptImage(file));
    }
}
