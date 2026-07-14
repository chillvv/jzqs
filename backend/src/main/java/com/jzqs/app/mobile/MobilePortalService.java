package com.jzqs.app.mobile;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleResponse;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.mobile.api.MobileDeliverySubscriptionAuthorizeResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileDefaultAddressResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderAddressChangeResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileSubscribeMessageTestResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceReplaceResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceResponse;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import com.jzqs.app.mobile.api.RiderQueueItemActionResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface MobilePortalService {
    MobileHomeResponse customerHome(String phone);

    MobileHomeResponse guestHome();

    MobileHomeResponse customerHome(long customerId);

    PageResponse<MobileMenuItemResponse> publishedMenus(String serveDate);

    MobileCurrentWeekResponse currentWeekMenu();

    MobileTomorrowMenuResponse tomorrowMenu();

    List<MobileWeekMenuDayResponse> weekMenus(String startDate);

    PageResponse<MobileOrderItemResponse> customerOrders(String phone, String status);

    PageResponse<MobileOrderItemResponse> customerOrders(long customerId, String status);

    MobileCreateOrderResponse createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note);

    MobileCreateOrderResponse createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note);

    MobileDeliverySubscriptionAuthorizeResponse authorizeDeliverySubscription(long customerId, long orderId, String templateId, String acceptResult);

    MobileSubscribeMessageTestResponse sendSubscribeMessageTest(long customerId, String templateId, String acceptResult);

    int sendScheduledDeliverySubscribeMessages(String mealPeriod);

    int sendAllDeliveredPendingSubscriptions();

    OrderActionResponse cancelMiniappOrder(String phone, long orderId);

    OrderActionResponse cancelMiniappOrder(long customerId, long orderId);

    OrderActionResponse deleteMiniappOrder(long customerId, long orderId);

    MobileCreateAfterSaleResponse createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request);

    List<MobileAfterSaleItemResponse> customerAfterSales(long customerId);

    List<MobileAddressResponse> customerAddresses(String phone);

    List<MobileAddressResponse> customerAddresses(long customerId);

    MobileAddressResponse saveCustomerAddress(String phone, String contactName, String contactPhone, String addressLine, String areaCode, boolean isDefault);

    MobileAddressResponse saveCustomerAddress(long customerId, String contactName, String contactPhone, String addressLine, String areaCode, boolean isDefault);

    MobileDefaultAddressResponse setDefaultAddress(String phone, long addressId);

    MobileDefaultAddressResponse setDefaultAddress(long customerId, long addressId);

    MobileOrderAddressChangeResponse changeCustomerOrderAddress(long customerId, long orderId, long addressId);

    PageResponse<WalletTransactionResponse> walletTransactions(String phone);

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId);

    PageResponse<RiderTaskItemResponse> riderTasks(String riderName);

    RiderBatchSummaryResponse riderSummary(String riderName, String serveDate);

    PageResponse<RiderQueueItemResponse> riderQueue(String riderName, String serveDate);

    RiderQueueItemResponse riderQueueItem(long queueItemId, String riderName, String serveDate, Long mealSlotOrderId);

    RiderAddressReferenceResponse riderAddressReference(String riderName, long addressId);

    RiderAddressReferenceBatchSaveResponse saveBatchAddressReferenceImage(String riderName, RiderBatchAddressReferenceRequest request);

    RiderAddressReferenceReplaceResponse replaceAddressReferenceImage(String riderName, long addressId, String referenceImageUrl);

    RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file);

    DeliveryReceiptRecordResponse submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt);

    DeliveryReceiptRecordResponse updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt);

    DeliveryReceiptDeleteResponse deleteRiderReceiptImage(long mealSlotOrderId, String riderName);

    RiderQueueReorderResponse reorderRiderQueue(String riderName, List<Long> batchItemIds);

    RiderQueueItemActionResponse deferRiderQueueItem(String riderName, long batchItemId);

    RiderQueueItemActionResponse resumeRiderQueueItem(String riderName, long batchItemId);

    RiderDeliveryExceptionReportResponse reportDeliveryException(long mealSlotOrderId, String riderName, String exceptionType, String exceptionNote, List<String> exceptionImages);

    PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName);

    /**
     * 撤回订单状态
     * 将已完成的订单恢复为待配送状态
     * 
     * @param mealSlotOrderId 订单ID
     * @param riderName 骑手姓名
     * @return 操作结果
     */
    RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, String riderName);

    /**
     * 保存订单排序
     * 保存骑手自定义的配送顺序
     * 
     * @param riderName 骑手姓名
     * @param mealPeriod 餐期
     * @param batchItemIds 排序后的批次项ID列表
     * @return 操作结果
     */
    RiderOrderSequenceSaveResponse saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds);
}
