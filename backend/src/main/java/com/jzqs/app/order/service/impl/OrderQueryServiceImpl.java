package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import com.jzqs.app.order.persistence.OrderQueryRepository;
import com.jzqs.app.order.service.OrderQueryService;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.subscription.api.SubscriptionPreviewCheckResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {
    private final OrderQueryRepository orderQueryRepository;

    public OrderQueryServiceImpl(OrderQueryRepository orderQueryRepository) {
        this.orderQueryRepository = orderQueryRepository;
    }

    @Override
    public List<SubscriptionPreviewItem> subscriptionPreview(String serveDate) {
        return orderQueryRepository.findSubscriptionPreview(LocalDate.parse(serveDate));
    }

    @Override
    public SubscriptionPreviewCheckResponse subscriptionPreviewCheck(String serveDate) {
        List<SubscriptionPreviewItem> previewItems = subscriptionPreview(serveDate);

        List<SubscriptionPreviewCheckResponse.InsufficientCustomer> insufficientCustomers = new ArrayList<>();
        int sufficientCount = 0;

        for (SubscriptionPreviewItem item : previewItems) {
            if (!item.hasBalance()) {
                insufficientCustomers.add(new SubscriptionPreviewCheckResponse.InsufficientCustomer(
                    item.customerId(),
                    item.customerName(),
                    item.customerPhone(),
                    item.remainingMeals(),
                    1,
                    item.mealPeriod()
                ));
            } else {
                sufficientCount++;
            }
        }

        return new SubscriptionPreviewCheckResponse(
            previewItems.size(),
            sufficientCount,
            insufficientCustomers.size(),
            insufficientCustomers
        );
    }

    @Override
    public OrderPrepStatsResponse prepStats() {
        return orderQueryRepository.loadPrepStats();
    }

    @Override
    public PageResponse<OrderPrepItemResponse> prepPage(String serveDate) {
        LocalDate targetDate;
        if (serveDate == null || serveDate.isEmpty()) {
            targetDate = LocalDate.now().plusDays(1);
        } else {
            targetDate = LocalDate.parse(serveDate);
        }
        return orderQueryRepository.findPrepPage(targetDate);
    }

    @Override
    public List<SubscriptionConfirmationItem> subscriptionConfirmations(String serveDate) {
        return orderQueryRepository.findSubscriptionConfirmations(LocalDate.parse(serveDate));
    }

    @Override
    public OrderNotesResponse orderNotes(long orderId) {
        if (!orderQueryRepository.orderExists(orderId)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单不存在");
        }
        return orderQueryRepository.findOrderNotes(orderId);
    }

    @Override
    public List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }
        return orderQueryRepository.searchManualCreateCustomers(normalizedKeyword);
    }
}
