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
import com.jzqs.app.mobile.api.MobileWalletBalanceResponse;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface MobilePortalService {
    MobileHomeResponse customerHome(String phone);

    MobileHomeResponse guestHome();

    MobileHomeResponse customerHome(long customerId);

    PageResponse<MobileMenuItemResponse> publishedMenus(String serveDate);

    MobileCurrentWeekResponse currentWeekMenu();

    MobileCurrentWeekResponse nextWeekMenu();

    MobileTomorrowMenuResponse tomorrowMenu();

    List<MobileWeekMenuDayResponse> weekMenus(String startDate);

    PageResponse<MobileOrderItemResponse> customerOrders(String phone, String status);

    PageResponse<MobileOrderItemResponse> customerOrders(long customerId, String status);

    MobileCreateOrderResponse createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note, int quantity);

    MobileCreateOrderResponse createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note, int quantity);

    MobileCreateOrderResponse createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, Long addressId, String note, int quantity);

    MobileDeliverySubscriptionAuthorizeResponse authorizeDeliverySubscription(long customerId, long orderId, String templateId, String acceptResult);

    MobileDeliverySubscriptionAuthorizeResponse authorizeNightlySubscription(long customerId, String templateId, String acceptResult);

    /** 查询用户是否已授权「每晚用餐提醒」订阅（status = AUTHORIZED）。 */
    boolean isNightlySubscribed(long customerId);

    /** 取消「每晚用餐提醒」订阅。 */
    void cancelNightlySubscription(long customerId);

    /** 同步微信侧真实订阅状态到后端（enabled=true 恢复 AUTHORIZED，否则置 CANCELLED）。 */
    void syncNightlySubscription(long customerId, boolean enabled, String templateId);

    MobileSubscribeMessageTestResponse sendSubscribeMessageTest(long customerId, String templateId, String acceptResult, String type);

    int sendScheduledDeliverySubscribeMessages(String mealPeriod);

    int sendAllDeliveredPendingSubscriptions();

    OrderActionResponse cancelMiniappOrder(String phone, long orderId);

    OrderActionResponse cancelMiniappOrder(long customerId, long orderId);

    OrderActionResponse deleteMiniappOrder(long customerId, long orderId);

    MobileCreateAfterSaleResponse createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request);

    List<MobileAfterSaleItemResponse> customerAfterSales(long customerId);

    List<MobileAddressResponse> customerAddresses(String phone);

    List<MobileAddressResponse> customerAddresses(long customerId);

    MobileAddressResponse saveCustomerAddress(String phone, String contactName, String contactPhone, String addressLine, String doorNumber, String areaCode, boolean isDefault, BigDecimal latitude, BigDecimal longitude);

    MobileAddressResponse saveCustomerAddress(long customerId, String contactName, String contactPhone, String addressLine, String doorNumber, String areaCode, boolean isDefault, BigDecimal latitude, BigDecimal longitude);

    MobileAddressResponse updateCustomerAddress(long customerId, long addressId, String contactName, String contactPhone, String addressLine, String doorNumber, String areaCode, boolean isDefault, BigDecimal latitude, BigDecimal longitude);

    void deleteCustomerAddress(long customerId, long addressId);

    MobileDefaultAddressResponse setDefaultAddress(String phone, long addressId);

    MobileDefaultAddressResponse setDefaultAddress(long customerId, long addressId);

    MobileOrderAddressChangeResponse changeCustomerOrderAddress(long customerId, long orderId, long addressId);

    MobileOrderAddressChangeResponse changeCustomerOrderAddressByMerchant(long customerId, long orderId, long addressId);

    PageResponse<WalletTransactionResponse> walletTransactions(String phone);

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId);

    MobileWalletBalanceResponse walletBalance(long customerId);

    PageResponse<RiderTaskItemResponse> riderTasks(Long riderId);

    RiderBatchSummaryResponse riderSummary(Long riderId, String serveDate);

    PageResponse<RiderQueueItemResponse> riderQueue(Long riderId, String serveDate);

    RiderQueueItemResponse riderQueueItem(long queueItemId, Long riderId, String serveDate, Long mealSlotOrderId);

    RiderAddressReferenceResponse riderAddressReference(Long riderId, long addressId);

    RiderAddressReferenceBatchSaveResponse saveBatchAddressReferenceImage(Long riderId, RiderBatchAddressReferenceRequest request);

    RiderAddressReferenceReplaceResponse replaceAddressReferenceImage(Long riderId, long addressId, String referenceImageUrl);

    RiderDeliveryUploadResponse uploadRiderReceipt(Long riderId, MultipartFile file);

    DeliveryReceiptRecordResponse submitRiderReceipt(long mealSlotOrderId, Long riderId, String receiptFileKey, String receiptNote, String deliveredAt);

    DeliveryReceiptRecordResponse updateRiderReceipt(long mealSlotOrderId, Long riderId, String receiptFileKey, String receiptNote, String deliveredAt);

    DeliveryReceiptDeleteResponse deleteRiderReceiptImage(long mealSlotOrderId, Long riderId);

    RiderQueueReorderResponse reorderRiderQueue(Long riderId, List<Long> batchItemIds);

    RiderQueueItemActionResponse deferRiderQueueItem(Long riderId, long batchItemId);

    RiderQueueItemActionResponse resumeRiderQueueItem(Long riderId, long batchItemId);

    RiderDeliveryExceptionReportResponse reportDeliveryException(long mealSlotOrderId, Long riderId, String exceptionType, String exceptionNote, List<String> exceptionImages);

    PageResponse<RiderTaskItemResponse> riderCompletedToday(Long riderId);

    /**
     * 撤回订单状态
     * 将已完成的订单恢复为待配送状态
     * 
     * @param mealSlotOrderId 订单ID
     * @param riderName 骑手姓名
     * @return 操作结果
     */
    RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, Long riderId);

    /**
     * 保存订单排序
     * 保存骑手自定义的配送顺序
     * 
     * @param riderName 骑手姓名
     * @param mealPeriod 餐期
     * @param batchItemIds 排序后的批次项ID列表
     * @return 操作结果
     */
    RiderOrderSequenceSaveResponse saveOrderSequence(Long riderId, String mealPeriod, List<Long> batchItemIds);
}
