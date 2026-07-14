package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.order.api.SubscriptionActionResponse;
import com.jzqs.app.order.api.SubscriptionBulkImportResponse;
import com.jzqs.app.order.api.SubscriptionImportItem;
import com.jzqs.app.order.persistence.OrderSubscriptionRepository;
import com.jzqs.app.order.service.OrderSubscriptionImportService;
import com.jzqs.app.order.service.OrderSubscriptionService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSubscriptionServiceImpl implements OrderSubscriptionService {
    private static final String CONFIRMED_BY = "后台客服";

    private final OrderSubscriptionRepository orderSubscriptionRepository;
    private final OrderSubscriptionImportService orderSubscriptionImportExecutor;

    public OrderSubscriptionServiceImpl(
        OrderSubscriptionRepository orderSubscriptionRepository,
        OrderSubscriptionImportService orderSubscriptionImportExecutor
    ) {
        this.orderSubscriptionRepository = orderSubscriptionRepository;
        this.orderSubscriptionImportExecutor = orderSubscriptionImportExecutor;
    }

    @Override
    @Transactional
    public SubscriptionActionResponse confirmSubscription(long confirmationId) {
        Integer updated = orderSubscriptionRepository.confirmSubscription(confirmationId, CONFIRMED_BY);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_FOUND, "待确认记录不存在");
        }
        return new SubscriptionActionResponse(confirmationId, "CONFIRMED");
    }

    @Override
    @Transactional
    public SubscriptionActionResponse cancelSubscription(long confirmationId, String cancelReason) {
        Integer updated = orderSubscriptionRepository.cancelSubscription(confirmationId, cancelReason);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_FOUND, "待确认记录不存在");
        }
        return new SubscriptionActionResponse(confirmationId, "CANCELLED");
    }

    @Override
    @Transactional
    public SubscriptionBulkImportResponse bulkImportSubscription(String serveDate, List<SubscriptionImportItem> items) {
        int successCount = 0;
        List<SubscriptionBulkImportResponse.FailureItem> failures = new ArrayList<>();

        for (SubscriptionImportItem item : items) {
            try {
                Integer remainingMeals = orderSubscriptionRepository.findRemainingMeals(item.customerId());
                String customerName = fallbackCustomerName(item.customerId());
                if (remainingMeals == null || remainingMeals <= 0) {
                    failures.add(new SubscriptionBulkImportResponse.FailureItem(
                        item.customerId(),
                        customerName,
                        "余额不足或未找到可用套餐",
                        remainingMeals != null ? remainingMeals : 0,
                        1
                    ));
                    continue;
                }

                String addressLine = orderSubscriptionRepository.findAddressLine(item.addressId());
                if (addressLine == null || addressLine.isBlank()) {
                    failures.add(new SubscriptionBulkImportResponse.FailureItem(
                        item.customerId(),
                        customerName,
                        "配送地址不存在",
                        remainingMeals,
                        1
                    ));
                    continue;
                }
                orderSubscriptionImportExecutor.importSingleItem(item, serveDate, addressLine);
                successCount++;
            } catch (Exception e) {
                failures.add(new SubscriptionBulkImportResponse.FailureItem(
                    item.customerId(),
                    fallbackCustomerName(item.customerId()),
                    fallbackFailureReason(e),
                    0,
                    1
                ));
            }
        }

        return new SubscriptionBulkImportResponse(successCount, failures.size(), failures);
    }

    private String fallbackCustomerName(long customerId) {
        String customerName = orderSubscriptionRepository.findCustomerName(customerId);
        return customerName == null || customerName.isBlank() ? "未知" : customerName;
    }

    private String fallbackFailureReason(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "导入失败，请稍后重试" : message;
    }
}
