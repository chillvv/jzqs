package com.jzqs.app.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.aftersale.service.AftersaleService;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.delivery.api.DeliveryReceiptRecordResponse;
import com.jzqs.app.mobile.api.MobileAddressResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleResponse;
import com.jzqs.app.mobile.api.MobileCreateOrderResponse;
import com.jzqs.app.mobile.api.MobileCurrentWeekResponse;
import com.jzqs.app.mobile.api.MobileDefaultAddressResponse;
import com.jzqs.app.mobile.api.MobileDeliverySubscriptionAuthorizeResponse;
import com.jzqs.app.mobile.api.MobileHomeResponse;
import com.jzqs.app.mobile.api.MobileMenuItemResponse;
import com.jzqs.app.mobile.api.MobileOrderAddressChangeResponse;
import com.jzqs.app.mobile.api.MobileOrderItemResponse;
import com.jzqs.app.mobile.api.MobileSubscribeMessageTestResponse;
import com.jzqs.app.mobile.api.MobileTomorrowMenuResponse;
import com.jzqs.app.mobile.api.MobileWeekMenuDayResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceBatchSaveResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceReplaceResponse;
import com.jzqs.app.mobile.api.RiderAddressReferenceResponse;
import com.jzqs.app.mobile.api.RiderBatchAddressReferenceRequest;
import com.jzqs.app.mobile.api.RiderBatchSummaryResponse;
import com.jzqs.app.mobile.api.RiderDeliveryExceptionReportResponse;
import com.jzqs.app.mobile.api.RiderDeliveryUploadResponse;
import com.jzqs.app.mobile.api.RiderOrderSequenceSaveResponse;
import com.jzqs.app.mobile.api.RiderOrderStatusRevertResponse;
import com.jzqs.app.mobile.api.RiderQueueItemActionResponse;
import com.jzqs.app.mobile.api.RiderQueueItemResponse;
import com.jzqs.app.mobile.api.RiderQueueReorderResponse;
import com.jzqs.app.mobile.api.RiderTaskItemResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MobilePortalServiceImpl implements MobilePortalService {
    private final JdbcTemplate jdbcTemplate;
    private final OrderPrepService orderPrepService;
    private final RiderQueueSupport riderQueueSupport;
    private final RiderReceiptStorageSupport riderReceiptStorageSupport;
    private final MobileCustomerQueryModule mobileCustomerQueryModule;
    private final RiderDeliveryEvidenceModule riderDeliveryEvidenceModule;
    private final RiderOrderStatusRevertModule riderOrderStatusRevertModule;
    private final RiderOrderSequenceModule riderOrderSequenceModule;
    private final AftersaleService aftersaleService;
    private final RealtimeAudienceModule realtimeAudienceModule;
    private final DeliverySubscriptionModule deliverySubscriptionModule;
    private final MiniappOrderModule miniappOrderModule;
    private final MobileAddressModule mobileAddressModule;
    private final LocalTime selfOrderCutoffTime;

    public MobilePortalServiceImpl(
        JdbcTemplate jdbcTemplate,
        OrderPrepService orderPrepService,
        RiderQueueSupport riderQueueSupport,
        RiderReceiptStorageSupport riderReceiptStorageSupport,
        ObjectMapper objectMapper,
        MobileCustomerQueryModule mobileCustomerQueryModule,
        RiderDeliveryEvidenceModule riderDeliveryEvidenceModule,
        RiderOrderStatusRevertModule riderOrderStatusRevertModule,
        RiderOrderSequenceModule riderOrderSequenceModule,
        AftersaleService aftersaleService,
        RealtimeAudienceModule realtimeAudienceModule,
        DeliverySubscriptionModule deliverySubscriptionModule,
        MiniappOrderModule miniappOrderModule,
        MobileAddressModule mobileAddressModule,
        @org.springframework.beans.factory.annotation.Value("${app.mobile.self-order-cutoff:23:00}") String selfOrderCutoff
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderPrepService = orderPrepService;
        this.riderQueueSupport = riderQueueSupport;
        this.riderReceiptStorageSupport = riderReceiptStorageSupport;
        this.mobileCustomerQueryModule = mobileCustomerQueryModule;
        this.riderDeliveryEvidenceModule = riderDeliveryEvidenceModule;
        this.riderOrderStatusRevertModule = riderOrderStatusRevertModule;
        this.riderOrderSequenceModule = riderOrderSequenceModule;
        this.aftersaleService = aftersaleService;
        this.realtimeAudienceModule = realtimeAudienceModule;
        this.deliverySubscriptionModule = deliverySubscriptionModule;
        this.miniappOrderModule = miniappOrderModule;
        this.mobileAddressModule = mobileAddressModule;
        this.selfOrderCutoffTime = LocalTime.parse(selfOrderCutoff);
    }

    @Override
    public MobileHomeResponse customerHome(String phone) {
        return customerHome(findCustomerIdByPhone(phone));
    }

    @Override
    public MobileHomeResponse guestHome() {
        return mobileCustomerQueryModule.guestHome();
    }

    @Override
    public MobileHomeResponse customerHome(long customerId) {
        return mobileCustomerQueryModule.customerHome(customerId);
    }

    @Override
    public PageResponse<MobileMenuItemResponse> publishedMenus(String serveDate) {
        return mobileCustomerQueryModule.publishedMenus(serveDate);
    }

    @Override
    public MobileCurrentWeekResponse currentWeekMenu() {
        return mobileCustomerQueryModule.currentWeekMenu();
    }

    @Override
    public MobileTomorrowMenuResponse tomorrowMenu() {
        return mobileCustomerQueryModule.tomorrowMenu();
    }

    @Override
    public List<MobileWeekMenuDayResponse> weekMenus(String startDate) {
        return mobileCustomerQueryModule.weekMenus(startDate);
    }

    @Override
    public PageResponse<MobileOrderItemResponse> customerOrders(String phone, String status) {
        return customerOrders(findCustomerIdByPhone(phone), status);
    }

    @Override
    public PageResponse<MobileOrderItemResponse> customerOrders(long customerId, String status) {
        return mobileCustomerQueryModule.customerOrders(customerId, status);
    }

    @Override
    @Transactional
    public MobileCreateOrderResponse createMiniappOrder(String phone, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        return createMiniappOrder(findCustomerIdByPhone(phone), serveDate, mealPeriod, deliveryAddress, note);
    }

    @Override
    @Transactional
    public MobileCreateOrderResponse createMiniappOrder(long customerId, String serveDate, String mealPeriod, String deliveryAddress, String note) {
        return miniappOrderModule.createOrder(customerId, serveDate, mealPeriod, deliveryAddress, note);
    }

    @Override
    @Transactional
    public MobileDeliverySubscriptionAuthorizeResponse authorizeDeliverySubscription(long customerId, long orderId, String templateId, String acceptResult) {
        if (!isAcceptedSubscribeResult(acceptResult)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持保存已同意的订阅授权");
        }
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ? AND do.customer_id = ?
                """,
            Integer.class,
            orderId,
            customerId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应订单");
        }
        deliverySubscriptionModule.authorizeSubscription(customerId, orderId, templateId);
        return new MobileDeliverySubscriptionAuthorizeResponse(orderId, "AUTHORIZED");
    }

    @Override
    @Transactional
    public MobileSubscribeMessageTestResponse sendSubscribeMessageTest(long customerId, String templateId, String acceptResult) {
        if (!isAcceptedSubscribeResult(acceptResult)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持发送已同意的订阅测试消息");
        }
        deliverySubscriptionModule.sendTestMessage(customerId);
        return new MobileSubscribeMessageTestResponse("SENT", safeString(templateId).trim(), "pages/profile/index");
    }

    @Override
    @Transactional
    public int sendScheduledDeliverySubscribeMessages(String mealPeriod) {
        return deliverySubscriptionModule.sendScheduledMessages(mealPeriod);
    }

    @Override
    @Transactional
    public int sendAllDeliveredPendingSubscriptions() {
        return deliverySubscriptionModule.sendAllDeliveredPendingSubscriptions();
    }

    @Override
    @Transactional
    public OrderActionResponse cancelMiniappOrder(String phone, long orderId) {
        return cancelMiniappOrder(findCustomerIdByPhone(phone), orderId);
    }

    @Override
    @Transactional
    public OrderActionResponse cancelMiniappOrder(long customerId, long orderId) {
        CustomerOrderStatusRow order = jdbcTemplate.query(
            """
                SELECT do.serve_date, mso.status
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE mso.id = ? AND do.customer_id = ?
                """,
            ps -> {
                ps.setLong(1, orderId);
                ps.setLong(2, customerId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new CustomerOrderStatusRow(
                    rs.getObject("serve_date", LocalDate.class),
                    rs.getString("status")
                );
            }
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该订单");
        }
        if (!MiniappCustomerCancelGuard.canCustomerCancel(LocalDateTime.now(), order.serveDate(), order.status(), selfOrderCutoffTime)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该订单已过用户可取消时间，仅商家后台可处理");
        }
        OrderActionResponse result = orderPrepService.cancelOrder(orderId);
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return result;
    }

    @Override
    @Transactional
    public MobileCreateAfterSaleResponse createAfterSale(long customerId, long orderId, MobileCreateAfterSaleRequest request) {
        return aftersaleService.createMobileCase(customerId, orderId, request);
    }

    @Override
    @Transactional
    public OrderActionResponse deleteMiniappOrder(long customerId, long orderId) {
        int updated = jdbcTemplate.update(
            """
                UPDATE meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                SET mso.visible_to_customer = FALSE
                WHERE mso.id = ? AND do.customer_id = ?
                  AND mso.status IN ('CANCELLED', 'REFUNDED')
                """,
            orderId,
            customerId
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法删除该订单（可能状态不符或订单不存在）");
        }
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        publishCustomerEvent("customer.wallet.changed", customerId, orderId);
        return new OrderActionResponse(orderId, "HIDDEN");
    }

    @Override
    public List<MobileAfterSaleItemResponse> customerAfterSales(long customerId) {
        return aftersaleService.customerCases(customerId);
    }

    @Override
    public List<MobileAddressResponse> customerAddresses(String phone) {
        return customerAddresses(findCustomerIdByPhone(phone));
    }

    @Override
    public List<MobileAddressResponse> customerAddresses(long customerId) {
        return mobileAddressModule.customerAddresses(customerId);
    }

    @Override
    @Transactional
    public MobileAddressResponse saveCustomerAddress(
        String phone,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        return saveCustomerAddress(findCustomerIdByPhone(phone), contactName, contactPhone, addressLine, areaCode, isDefault);
    }

    @Override
    @Transactional
    public MobileAddressResponse saveCustomerAddress(
        long customerId,
        String contactName,
        String contactPhone,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
        return mobileAddressModule.saveCustomerAddress(customerId, contactName, contactPhone, addressLine, areaCode, isDefault);
    }

    @Override
    @Transactional
    public MobileDefaultAddressResponse setDefaultAddress(String phone, long addressId) {
        return setDefaultAddress(findCustomerIdByPhone(phone), addressId);
    }

    @Override
    @Transactional
    public MobileDefaultAddressResponse setDefaultAddress(long customerId, long addressId) {
        return mobileAddressModule.setDefaultAddress(customerId, addressId);
    }

    @Override
    @Transactional
    public MobileOrderAddressChangeResponse changeCustomerOrderAddress(long customerId, long orderId, long addressId) {
        MobileOrderAddressChangeResponse response = mobileAddressModule.changeCustomerOrderAddress(customerId, orderId, addressId);
        publishCustomerEvent("customer.order.changed", customerId, orderId);
        return response;
    }

    @Override
    public PageResponse<WalletTransactionResponse> walletTransactions(String phone) {
        return walletTransactions(findCustomerIdByPhone(phone));
    }

    @Override
    public PageResponse<WalletTransactionResponse> walletTransactions(long customerId) {
        return mobileCustomerQueryModule.walletTransactions(customerId);
    }

    @Override
    public PageResponse<RiderTaskItemResponse> riderTasks(String riderName) {
        return riderQueueSupport.riderTasks(riderName);
    }

    @Override
    public RiderBatchSummaryResponse riderSummary(String riderName, String serveDate) {
        return riderQueueSupport.riderSummary(riderName, serveDate);
    }

    @Override
    public PageResponse<RiderQueueItemResponse> riderQueue(String riderName, String serveDate) {
        return riderQueueSupport.riderQueue(riderName, serveDate);
    }

    @Override
    public RiderQueueItemResponse riderQueueItem(long queueItemId, String riderName, String serveDate, Long mealSlotOrderId) {
        return riderQueueSupport.riderQueueItem(queueItemId, riderName, serveDate, mealSlotOrderId);
    }

    @Override
    public RiderAddressReferenceResponse riderAddressReference(String riderName, long addressId) {
        return riderDeliveryEvidenceModule.riderAddressReference(riderName, addressId);
    }

    @Override
    @Transactional
    public RiderAddressReferenceBatchSaveResponse saveBatchAddressReferenceImage(String riderName, RiderBatchAddressReferenceRequest request) {
        return riderDeliveryEvidenceModule.saveBatchAddressReferenceImage(riderName, request);
    }

    @Override
    @Transactional
    public RiderAddressReferenceReplaceResponse replaceAddressReferenceImage(String riderName, long addressId, String referenceImageUrl) {
        return riderDeliveryEvidenceModule.replaceAddressReferenceImage(riderName, addressId, referenceImageUrl);
    }

    @Override
    public RiderDeliveryUploadResponse uploadRiderReceipt(String riderName, MultipartFile file) {
        return riderReceiptStorageSupport.uploadRiderReceipt(riderName, file);
    }

    @Override
    @Transactional
    public DeliveryReceiptRecordResponse submitRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        DeliveryReceiptRecordResponse result = riderDeliveryEvidenceModule.submitRiderReceipt(
            mealSlotOrderId,
            riderName,
            receiptFileKey,
            receiptNote,
            deliveredAt
        );
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Override
    @Transactional
    public DeliveryReceiptRecordResponse updateRiderReceipt(long mealSlotOrderId, String riderName, String receiptFileKey, String receiptNote, String deliveredAt) {
        DeliveryReceiptRecordResponse result = riderDeliveryEvidenceModule.updateRiderReceipt(
            mealSlotOrderId,
            riderName,
            receiptFileKey,
            receiptNote,
            deliveredAt
        );
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Override
    @Transactional
    public DeliveryReceiptDeleteResponse deleteRiderReceiptImage(long mealSlotOrderId, String riderName) {
        DeliveryReceiptDeleteResponse result = riderDeliveryEvidenceModule.deleteRiderReceiptImage(mealSlotOrderId, riderName);
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Override
    @Transactional
    public RiderQueueReorderResponse reorderRiderQueue(String riderName, List<Long> batchItemIds) {
        return riderQueueSupport.reorderRiderQueue(riderName, batchItemIds);
    }

    @Override
    @Transactional
    public RiderQueueItemActionResponse deferRiderQueueItem(String riderName, long batchItemId) {
        return riderQueueSupport.deferRiderQueueItem(riderName, batchItemId);
    }

    @Override
    @Transactional
    public RiderQueueItemActionResponse resumeRiderQueueItem(String riderName, long batchItemId) {
        return riderQueueSupport.resumeRiderQueueItem(riderName, batchItemId);
    }

    @Override
    @Transactional
    public RiderDeliveryExceptionReportResponse reportDeliveryException(
        long mealSlotOrderId,
        String riderName,
        String exceptionType,
        String exceptionNote,
        List<String> exceptionImages
    ) {
        return riderQueueSupport.reportDeliveryException(
            mealSlotOrderId,
            riderName,
            exceptionType,
            exceptionNote,
            exceptionImages
        );
    }

    @Override
    public PageResponse<RiderTaskItemResponse> riderCompletedToday(String riderName) {
        return riderQueueSupport.riderCompletedToday(riderName);
    }

    @Override
    public RiderOrderStatusRevertResponse revertOrderStatus(long mealSlotOrderId, String riderName) {
        RiderOrderStatusRevertResponse result = riderOrderStatusRevertModule.revertOrderStatus(mealSlotOrderId, riderName);
        publishCustomerOrderChanged(mealSlotOrderId);
        publishRiderEvent("dispatch.receipt.changed", riderName, mealSlotOrderId);
        return result;
    }

    @Override
    public RiderOrderSequenceSaveResponse saveOrderSequence(String riderName, String mealPeriod, List<Long> batchItemIds) {
        return riderOrderSequenceModule.saveOrderSequence(riderName, mealPeriod, batchItemIds);
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isAcceptedSubscribeResult(String acceptResult) {
        String normalized = safeString(acceptResult).trim();
        return "accept".equalsIgnoreCase(normalized)
            || "acceptWithAudio".equalsIgnoreCase(normalized)
            || "acceptWithAlert".equalsIgnoreCase(normalized);
    }

    private long findCustomerIdByPhone(String phone) {
        Long customerId = jdbcTemplate.query(
            "SELECT id FROM customers WHERE phone = ? AND active = TRUE",
            ps -> ps.setString(1, phone),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到对应客户");
        }
        return customerId;
    }

    private void publishRiderEvent(String eventType, String riderName, Object orderId) {
        realtimeAudienceModule.publishRiderEvent(eventType, riderName, orderId);
    }

    private void publishCustomerEvent(String eventType, long customerId, Object orderId) {
        realtimeAudienceModule.publishCustomerEvent(eventType, customerId, orderId);
    }

    private void publishCustomerOrderChanged(long mealSlotOrderId) {
        Long customerId = findCustomerIdByMealSlotOrderId(mealSlotOrderId);
        if (customerId == null) {
            return;
        }
        publishCustomerEvent("customer.order.changed", customerId, mealSlotOrderId);
    }

    private Long findCustomerIdByMealSlotOrderId(long mealSlotOrderId) {
        List<Long> customerIds = jdbcTemplate.query(
            """
                SELECT doo.customer_id
                FROM meal_slot_orders mso
                JOIN daily_orders doo ON doo.id = mso.daily_order_id
                WHERE mso.id = ?
                """,
            (rs, rowNum) -> rs.getLong("customer_id"),
            mealSlotOrderId
        );
        return customerIds.isEmpty() ? null : customerIds.get(0);
    }

    private record CustomerOrderStatusRow(LocalDate serveDate, String status) {
    }
}
