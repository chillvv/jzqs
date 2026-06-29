package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.api.BatchOperationResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.order.api.ManualCreateOrderResponse;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateRequest;
import com.jzqs.app.order.api.OrderMerchantRemarkUpdateResponse;
import com.jzqs.app.order.api.OrderNoteCreateRequest;
import com.jzqs.app.order.api.OrderNoteCreateResponse;
import com.jzqs.app.order.api.OrderProfileUpdateRequest;
import com.jzqs.app.order.api.OrderProfileUpdateResponse;
import com.jzqs.app.order.persistence.OrderOperationRepository;
import com.jzqs.app.order.persistence.OrderSupportRepository;
import com.jzqs.app.order.service.OrderNoteSnapshotService;
import com.jzqs.app.order.service.OrderOperationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOperationServiceImpl extends AbstractOrderPrepSupport implements OrderOperationService {
    private final OrderOperationRepository orderOperationRepository;
    private final OrderNoteSnapshotService orderNoteSnapshotService;

    public OrderOperationServiceImpl(
        OrderSupportRepository orderSupportRepository,
        OrderOperationRepository orderOperationRepository,
        OrderNoteSnapshotService orderNoteSnapshotService
    ) {
        super(orderSupportRepository);
        this.orderOperationRepository = orderOperationRepository;
        this.orderNoteSnapshotService = orderNoteSnapshotService;
    }

    @Override
    @Transactional
    public OrderMerchantRemarkUpdateResponse updateMerchantRemark(long orderId, OrderMerchantRemarkUpdateRequest request) {
        Integer updated = orderOperationRepository.updateMerchantRemark(orderId, request.merchantRemark());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return new OrderMerchantRemarkUpdateResponse(orderId, "UPDATED");
    }

    @Override
    @Transactional
    public OrderProfileUpdateResponse updateOrderProfile(long orderId, OrderProfileUpdateRequest request) {
        OrderOperationRepository.OrderProfileRecord current = orderOperationRepository.findOrderProfile(orderId);
        if (current == null) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }

        long customerId = current.customerId();
        String currentMealPeriod = stringValue(current.mealPeriod());
        int quantity = request.quantity() != null ? request.quantity() : current.quantity();
        String deliveryAddress = fallbackString(request.deliveryAddress(), current.deliveryAddress());
        String merchantRemark = request.merchantRemark() != null
            ? stringValue(request.merchantRemark())
            : stringValue(current.merchantRemark());
        boolean isPriority = request.priorityCustomer() != null
            ? booleanValue(request.priorityCustomer(), false)
            : current.priority();
        String status = request.status() != null
            ? stringValue(request.status())
            : stringValue(current.status());
        String mealPeriod = resolveMealPeriod(request.mealPeriod(), null, currentMealPeriod);
        long addressId = ensureCustomerAddress(customerId, deliveryAddress);

        Integer updated = orderOperationRepository.updateOrderProfile(
            orderId,
            mealPeriod,
            Math.max(1, quantity),
            addressId,
            merchantRemark,
            isPriority,
            status
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return new OrderProfileUpdateResponse(orderId, "UPDATED", addressId);
    }

    @Override
    @Transactional
    public OrderNoteCreateResponse addOrderNote(long orderId, OrderNoteCreateRequest request) {
        long customerId = requireOrderCustomerIdViaRepository(orderId);
        String noteType = normalizeOrderNoteType(request.noteType());
        String scopeType = normalizeOrderNoteScope(request.scopeType());
        String content = requireOrderNoteContent(request.content());

        orderOperationRepository.insertOrderNote(
            orderId,
            customerId,
            noteType,
            scopeType,
            content,
            "ADMIN"
        );
        return new OrderNoteCreateResponse(orderId, "CREATED");
    }

    @Override
    @Transactional
    public ManualCreateOrderResponse manualCreate(long customerId, Long addressId, String mealPeriod, String deliveryMealPeriod, String merchantRemark, String deliveryAddress, String source, int quantity, String serveDate) {
        LocalDate date = serveDate == null || serveDate.isBlank()
            ? LocalDate.now()
            : LocalDate.parse(serveDate);

        return manualCreateWithDate(
            customerId,
            mealPeriod,
            deliveryMealPeriod,
            merchantRemark,
            resolveManualCreateDeliveryAddress(customerId, addressId, deliveryAddress),
            source,
            date,
            quantity
        );
    }

    @Override
    @Transactional
    public OrderActionResponse cancelOrder(long orderId) {
        Integer updated = orderOperationRepository.cancelOrder(orderId);
        if (updated == 0) {
            return new OrderActionResponse(orderId, "NOT_FOUND");
        }

        List<Long> batchIds = orderOperationRepository.findDispatchBatchIds(orderId);
        if (!batchIds.isEmpty()) {
            orderOperationRepository.deleteDispatchBatchItems(orderId);
            long batchId = batchIds.get(0);
            orderOperationRepository.refreshDispatchBatchMetrics(batchId);
        }
        orderOperationRepository.deleteDispatchAssignments(orderId);

        OrderOperationRepository.WalletReserveContext orderInfo = orderOperationRepository.findWalletReserveContext(orderId);
        if (orderInfo != null) {
            Long customerId = orderInfo.customerId();
            int quantity = orderInfo.quantity();
            if (customerId != null) {
                Long walletId = findActiveWalletIdByCustomerId(customerId);
                if (walletId != null) {
                    orderOperationRepository.releaseReservedMeals(walletId, quantity);
                    insertWalletTransaction(walletId, "RELEASE", quantity, "系统", "取消订单释放餐次", orderId);
                }
            }
        }
        return new OrderActionResponse(orderId, "CANCELLED");
    }

    @Override
    @Transactional
    public OrderActionResponse deleteOrder(long orderId) {
        Integer count = orderOperationRepository.countOrderById(orderId);
        if (count == null || count == 0) {
            return new OrderActionResponse(orderId, "NOT_FOUND");
        }

        OrderOperationRepository.DeleteOrderContext orderInfo = orderOperationRepository.findDeleteOrderContext(orderId);
        if (orderInfo == null) {
            return new OrderActionResponse(orderId, "NOT_FOUND");
        }

        long dailyOrderId = orderInfo.dailyOrderId();
        Long customerId = orderInfo.customerId();
        String status = orderInfo.status();
        int quantity = orderInfo.quantity();

        if (!"CANCELLED".equals(status) && customerId != null) {
            Long walletId = findActiveWalletIdByCustomerId(customerId);
            if (walletId != null) {
                orderOperationRepository.releaseReservedMeals(walletId, quantity);
                insertWalletTransaction(walletId, "RELEASE", quantity, "系统", "删除订单释放餐次", orderId);
            }
        }

        List<Long> batchIds = orderOperationRepository.findDispatchBatchIds(orderId);
        if (!batchIds.isEmpty()) {
            orderOperationRepository.deleteDispatchBatchItems(orderId);
            for (long batchId : batchIds) {
                orderOperationRepository.refreshDispatchBatchMetrics(batchId);
            }
        }
        orderOperationRepository.deleteDispatchAssignments(orderId);

        orderOperationRepository.deleteDeliveryReceipts(orderId);
        orderOperationRepository.deleteCustomerDeliverySubscriptions(orderId);
        orderOperationRepository.deleteOrderNotes(orderId);
        orderOperationRepository.deleteMealSlotOrder(orderId);

        Integer remainingSlots = orderOperationRepository.countRemainingSlots(dailyOrderId);
        if (remainingSlots != null && remainingSlots == 0) {
            orderOperationRepository.deleteDailyOrder(dailyOrderId);
        }

        return new OrderActionResponse(orderId, "DELETED");
    }

    @Override
    @Transactional
    public BatchOperationResponse consumeOrders(List<Long> orderIds) {
        int successCount = 0;
        List<BatchOperationResponse.FailureItem> failures = new ArrayList<>();
        for (Long orderId : orderIds) {
            Integer count = orderOperationRepository.countDeliveredOrder(orderId);
            if (count == null || count == 0) {
                failures.add(new BatchOperationResponse.FailureItem(orderId, "ORDER_NOT_DELIVERED", "订单未送达，不能核销"));
                continue;
            }
            successCount++;
        }
        return new BatchOperationResponse(successCount, failures.size(), failures);
    }

    private long requireOrderCustomerIdViaRepository(long orderId) {
        List<Long> customerIds = orderOperationRepository.findOrderCustomerIds(orderId);
        if (customerIds.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return customerIds.get(0);
    }

    private ManualCreateOrderResponse manualCreateWithDate(long customerId, String mealPeriod, String deliveryMealPeriod, String merchantRemark, String deliveryAddress, String source, LocalDate serveDate, int quantity) {
        long addressId = ensureCustomerAddress(customerId, deliveryAddress);
        String resolvedMerchantRemark = resolveOrderMerchantRemark(customerId, merchantRemark);

        Long walletId = orderOperationRepository.findAvailableWalletId(customerId);
        if (walletId == null) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_ENOUGH, "该客户未开通套餐或套餐已过期");
        }
        Integer remainingMeals = orderOperationRepository.findRemainingMeals(walletId);
        if (remainingMeals < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_MEALS, "该客户剩余餐次不足（仅剩 " + remainingMeals + " 餐）");
        }

        Long existingDailyOrderId = orderOperationRepository.findExistingDailyOrderId(customerId, serveDate);

        long dailyOrderId = existingDailyOrderId == null
            ? orderOperationRepository.insertDailyOrder(customerId, serveDate, source)
            : existingDailyOrderId;

        String normalizedMealPeriod = normalizeMealPeriod(mealPeriod);
        String normalizedDeliveryMealPeriod = resolveDeliveryMealPeriod(deliveryMealPeriod, normalizedMealPeriod, normalizedMealPeriod);

        Long mergeTargetOrderId = orderOperationRepository.findMergeTargetOrderId(
            customerId,
            serveDate,
            normalizedMealPeriod,
            normalizedDeliveryMealPeriod,
            addressId
        );
        if (mergeTargetOrderId != null) {
            String mergedMerchantRemark = mergeOrderNote(
                stringValue(orderOperationRepository.findOrderMerchantRemark(mergeTargetOrderId)),
                resolvedMerchantRemark
            );
            orderOperationRepository.mergeOrderQuantityAndRemark(mergeTargetOrderId, quantity, mergedMerchantRemark);
            orderOperationRepository.increaseReservedMeals(walletId, quantity);
            insertWalletTransaction(
                walletId,
                "RESERVE",
                -quantity,
                "系统",
                "SUBSCRIPTION".equals(source) ? "固定订餐自动扣餐" : "代客录单加餐占用餐次",
                mergeTargetOrderId
            );
            return new ManualCreateOrderResponse(mergeTargetOrderId, "MERGED");
        }

        long mealSlotOrderId = orderOperationRepository.insertMealSlotOrder(
            dailyOrderId,
            normalizedMealPeriod,
            normalizedDeliveryMealPeriod,
            quantity,
            addressId,
            resolvedMerchantRemark,
            source,
            "SUBSCRIPTION".equals(source)
        );
        LocalDateTime snapshotTime = LocalDateTime.now();
        orderOperationRepository.increaseReservedMeals(walletId, quantity);
        insertWalletTransaction(
            walletId,
            "RESERVE",
            -quantity,
            "系统",
            "SUBSCRIPTION".equals(source) ? "固定订餐自动扣餐" : "代客录单占用餐次",
            mealSlotOrderId
        );
        String normalizedSnapshotNote = normalizeSnapshotNote(resolvedMerchantRemark);
        orderNoteSnapshotService.writeOrderSnapshot(
            mealSlotOrderId,
            customerId,
            "SUBSCRIPTION".equals(source) ? "系统" : "后台客服",
            null,
            normalizedSnapshotNote,
            List.of(),
            snapshotTime
        );
        return new ManualCreateOrderResponse(mealSlotOrderId, "PENDING_DISPATCH");
    }
}
