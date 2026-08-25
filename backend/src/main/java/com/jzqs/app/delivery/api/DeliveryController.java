package com.jzqs.app.delivery.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.delivery.service.DeliveryService;
import com.jzqs.app.mobile.MerchantDeliveryReceiptFacade;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final MerchantDeliveryReceiptFacade merchantDeliveryReceiptFacade;

    public DeliveryController(
        DeliveryService deliveryService,
        MerchantDeliveryReceiptFacade merchantDeliveryReceiptFacade
    ) {
        this.deliveryService = deliveryService;
        this.merchantDeliveryReceiptFacade = merchantDeliveryReceiptFacade;
    }

    /**
     * 商家后台提交/修改送达回执。与骑手端共用同一套可见性规则，
     * 因此商家上传的图片、回执说明与送达时间在用户端的表现与骑手上传完全一致。
     */
    @PostMapping("/receipt")
    public ApiResponse<DeliveryReceiptRecordResponse> receipt(@Valid @RequestBody DeliveryReceiptRequest request) {
        return ApiResponse.success(merchantDeliveryReceiptFacade.submitMerchantReceipt(
            request.mealSlotOrderId(),
            request.receiptUrl(),
            request.receiptNote() == null ? "" : request.receiptNote(),
            request.deliveredAt()
        ));
    }

    @PostMapping(value = "/receipt/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DeliveryReceiptUploadResponse> uploadReceipt(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(deliveryService.uploadReceiptImage(file));
    }

    @DeleteMapping("/receipt/{mealSlotOrderId}/image")
    public ApiResponse<DeliveryReceiptDeleteResponse> deleteReceiptImage(@PathVariable @Min(1) long mealSlotOrderId) {
        return ApiResponse.success(merchantDeliveryReceiptFacade.deleteMerchantReceiptImage(mealSlotOrderId));
    }
}
