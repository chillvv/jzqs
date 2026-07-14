package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.order.api.DeliveryReceiptDeleteResponse;
import com.jzqs.app.order.api.OrderSpecialDispatchResponse;
import com.jzqs.app.order.persistence.OrderDispatchRepository;
import com.jzqs.app.order.service.OrderDispatchService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderDispatchServiceImpl implements OrderDispatchService {
    private final OrderDispatchRepository orderDispatchRepository;
    private final DispatchService dispatchService;

    public OrderDispatchServiceImpl(OrderDispatchRepository orderDispatchRepository, DispatchService dispatchService) {
        this.orderDispatchRepository = orderDispatchRepository;
        this.dispatchService = dispatchService;
    }

    @Override
    @Transactional
    public OrderSpecialDispatchResponse updateSpecialDispatch(long orderId, String deliveryMealPeriod) {
        String normalizedDeliveryMealPeriod = normalizeMealPeriod(stringValue(deliveryMealPeriod));
        if (normalizedDeliveryMealPeriod.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择配送时间");
        }
        OrderDispatchRepository.SpecialDispatchContext context = requireSpecialDispatchContext(orderId);
        ensureSpecialDispatchMutable(context.status());
        orderDispatchRepository.resetDispatchFlow(orderId);
        orderDispatchRepository.updateSpecialDispatch(orderId, normalizedDeliveryMealPeriod);
        dispatchService.autoAssignPendingOrders(normalizedDeliveryMealPeriod);
        return new OrderSpecialDispatchResponse(orderId, "UPDATED", normalizedDeliveryMealPeriod);
    }

    @Override
    @Transactional
    public OrderSpecialDispatchResponse resetSpecialDispatch(long orderId) {
        OrderDispatchRepository.SpecialDispatchContext context = requireSpecialDispatchContext(orderId);
        ensureSpecialDispatchMutable(context.status());
        String restoredDeliveryMealPeriod = normalizeMealPeriod(stringValue(context.mealPeriod()));
        orderDispatchRepository.resetDispatchFlow(orderId);
        orderDispatchRepository.resetSpecialDispatch(orderId);
        dispatchService.autoAssignPendingOrders(restoredDeliveryMealPeriod);
        return new OrderSpecialDispatchResponse(orderId, "RESET", restoredDeliveryMealPeriod);
    }

    @Override
    @Transactional
    public DeliveryReceiptDeleteResponse deleteDeliveryReceipt(long orderId) {
        Optional<String> orderStatus = orderDispatchRepository.findOrderStatus(orderId);
        if (orderStatus.isEmpty()) {
            return new DeliveryReceiptDeleteResponse(orderId, "NOT_FOUND", "", false);
        }

        Optional<OrderDispatchRepository.DeliveryReceiptRecord> latestReceipt = orderDispatchRepository.findLatestDeliveryReceipt(orderId);
        if (latestReceipt.isEmpty()) {
            return new DeliveryReceiptDeleteResponse(orderId, orderStatus.get(), "", false);
        }

        orderDispatchRepository.clearLatestDeliveryReceipt(latestReceipt.get().id());

        return new DeliveryReceiptDeleteResponse(orderId, orderStatus.get(), "", true);
    }

    private OrderDispatchRepository.SpecialDispatchContext requireSpecialDispatchContext(long orderId) {
        return orderDispatchRepository.findSpecialDispatchContext(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在"));
    }

    private void ensureSpecialDispatchMutable(String status) {
        if (!"PENDING_DISPATCH".equals(status) && !"DISPATCHING".equals(status)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前状态不允许特殊处理");
        }
    }

    private String normalizeMealPeriod(String mealPeriod) {
        if ("DINNER".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("晚餐")) {
            return "DINNER";
        }
        if ("LUNCH".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("午餐")) {
            return "LUNCH";
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
