package com.jzqs.app.mobile;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
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

    Map<String, Object> createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note);

    Map<String, Object> createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note);

    Map<String, Object> cancelMiniappOrder(String phone, long orderId);

    Map<String, Object> cancelMiniappOrder(long customerId, long orderId);

    Map<String, Object> deleteMiniappOrder(long customerId, long orderId);

    Map<String, Object> createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request);

    List<MobileAfterSaleItemResponse> customerAfterSales(long customerId);

    List<MobileAddressResponse> customerAddresses(String phone);

    List<MobileAddressResponse> customerAddresses(long customerId);

    MobileAddressResponse saveCustomerAddress(String phone, String contactName, String contactPhone, String addressLine, String areaCode, boolean isDefault);

    MobileAddressResponse saveCustomerAddress(long customerId, String contactName, String contactPhone, String addressLine, String areaCode, boolean isDefault);

    Map<String, Object> setDefaultAddress(String phone, long addressId);

    Map<String, Object> setDefaultAddress(long customerId, long addressId);

    PageResponse<WalletTransactionResponse> walletTransactions(String phone);

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId);

    PageResponse<RiderTaskItemResponse> riderTasks(String riderName);

    RiderBatchSummaryResponse riderSummary(String riderName);

    PageResponse<RiderQueueItemResponse> riderQueue(String riderName);

    RiderQueueItemResponse riderQueueItem(long batchItemId, String riderName);

    RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file);

    Map<String, Object> submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt);

    Map<String, Object> updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt);

    Map<String, Object> deleteRiderReceiptImage(long mealSlotOrderId, String riderName);

    Map<String, Object> reorderRiderQueue(String riderName, List<Long> batchItemIds);

    Map<String, Object> deferRiderQueueItem(String riderName, long batchItemId);

    Map<String, Object> resumeRiderQueueItem(String riderName, long batchItemId);

    Map<String, Object> reportDeliveryException(long mealSlotOrderId, String riderName, String exceptionType, String exceptionNote, List<String> exceptionImages);

    PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName);

    /**
     * 撤回订单状态
     * 将已完成的订单恢复为待配送状态
     * 
     * @param mealSlotOrderId 订单ID
     * @param riderName 骑手姓名
     * @return 操作结果
     */
    Map<String, Object> revertOrderStatus(long mealSlotOrderId, String riderName);

    /**
     * 保存订单排序
     * 保存骑手自定义的配送顺序
     * 
     * @param riderName 骑手姓名
     * @param mealPeriod 餐期
     * @param batchItemIds 排序后的批次项ID列表
     * @return 操作结果
     */
    Map<String, Object> saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds);
}
