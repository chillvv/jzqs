package com.jzqs.app.order.service.impl;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.order.persistence.OrderSupportRepository;
import java.util.List;

abstract class AbstractOrderPrepSupport {
    protected final OrderSupportRepository orderSupportRepository;

    protected AbstractOrderPrepSupport(OrderSupportRepository orderSupportRepository) {
        this.orderSupportRepository = orderSupportRepository;
    }

    protected Long findActiveWalletIdByCustomerId(long customerId) {
        return orderSupportRepository.findActiveWalletIdByCustomerId(customerId);
    }

    protected long ensureCustomerAddress(long customerId, String deliveryAddress) {
        List<Long> existingIds = orderSupportRepository.findCustomerAddressIds(customerId, deliveryAddress);
        if (!existingIds.isEmpty()) {
            return existingIds.get(0);
        }
        OrderSupportRepository.CustomerProfile customer = orderSupportRepository.findCustomerProfile(customerId);
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该客户");
        }
        return orderSupportRepository.insertCustomerAddress(
            customerId,
            stringValue(customer.name()),
            stringValue(customer.phone()),
            deliveryAddress,
            deliveryAddress.contains("高新区") ? "高新区" : "老城区"
        );
    }

    protected String resolveManualCreateDeliveryAddress(long customerId, Long addressId, String deliveryAddress) {
        if (addressId != null && addressId > 0) {
            List<String> addressLines = orderSupportRepository.findAddressLines(addressId, customerId);
            if (!addressLines.isEmpty()) {
                return addressLines.get(0);
            }
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "未找到该客户地址");
        }

        String normalizedAddress = stringValue(deliveryAddress);
        if (normalizedAddress.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择配送地址");
        }
        return normalizedAddress;
    }

    protected void insertWalletTransaction(long walletId, String transactionType, int mealDelta, String operatorName, String remark, Long relatedOrderId) {
        orderSupportRepository.insertWalletTransaction(walletId, transactionType, mealDelta, operatorName, remark, relatedOrderId);
    }

    protected String resolveMealPeriod(Object mealPeriodValue, Object legacyMealSummaryValue, String fallbackMealPeriod) {
        String normalizedMealPeriod = normalizeMealPeriod(stringValue(mealPeriodValue));
        if (!normalizedMealPeriod.isBlank()) {
            return normalizedMealPeriod;
        }
        String mealSummary = stringValue(legacyMealSummaryValue);
        if (mealSummary.isBlank()) {
            return fallbackMealPeriod;
        }
        return mealSummary.contains("晚餐") ? "DINNER" : "LUNCH";
    }

    protected String normalizeMealPeriod(String mealPeriod) {
        if ("DINNER".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("晚餐")) {
            return "DINNER";
        }
        if ("LUNCH".equalsIgnoreCase(mealPeriod) || mealPeriod.contains("午餐")) {
            return "LUNCH";
        }
        return "";
    }

    protected String resolveDeliveryMealPeriod(Object deliveryMealPeriodValue, String mealPeriod, String fallbackDeliveryMealPeriod) {
        String normalizedDeliveryMealPeriod = normalizeMealPeriod(stringValue(deliveryMealPeriodValue));
        if (!normalizedDeliveryMealPeriod.isBlank()) {
            return normalizedDeliveryMealPeriod;
        }
        String normalizedFallback = normalizeMealPeriod(fallbackDeliveryMealPeriod);
        if (!normalizedFallback.isBlank()) {
            return normalizedFallback;
        }
        return normalizeMealPeriod(mealPeriod);
    }

    protected String fallbackString(Object primaryValue, String fallback) {
        String normalized = stringValue(primaryValue);
        return normalized.isBlank() ? fallback : normalized;
    }

    protected String mergeOrderNote(String existingNote, String newNote) {
        String current = stringValue(existingNote);
        String incoming = stringValue(newNote);
        if (incoming.isBlank() || "-".equals(incoming)) {
            return current.isBlank() ? "-" : current;
        }
        if (current.isBlank() || "-".equals(current)) {
            return incoming;
        }
        if (current.contains(incoming)) {
            return current;
        }
        return current + "；" + incoming;
    }

    protected String normalizeSnapshotNote(String note) {
        String normalized = stringValue(note);
        return normalized.isBlank() || "-".equals(normalized) ? null : normalized;
    }

    protected String resolveOrderMerchantRemark(long customerId, String currentOrderMerchantRemark) {
        String normalizedCurrent = normalizeLegacyNote(currentOrderMerchantRemark);
        if (!normalizedCurrent.isBlank()) {
            return normalizedCurrent;
        }
        List<String> customerRemarks = orderSupportRepository.findCustomerMerchantRemarks(customerId);
        return customerRemarks.isEmpty() ? "" : normalizeLegacyNote(customerRemarks.get(0));
    }

    protected String normalizeLegacyNote(String value) {
        String normalized = stringValue(value);
        return "-".equals(normalized) ? "" : normalized;
    }

    protected String normalizeOrderNoteType(String noteType) {
        String normalized = stringValue(noteType).toUpperCase();
        if (!"MERCHANT".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单备注仅支持商家备注");
        }
        return normalized;
    }

    protected String normalizeOrderNoteScope(String scopeType) {
        String normalized = stringValue(scopeType).toUpperCase();
        if (!"ORDER_ONCE".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "订单备注仅支持单餐备注");
        }
        return normalized;
    }

    protected String requireOrderNoteContent(String content) {
        String normalized = stringValue(content);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注内容不能为空");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注内容不能超过255个字符");
        }
        return normalized;
    }

    protected String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    protected boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return fallback;
    }
}
