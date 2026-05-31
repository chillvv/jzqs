package com.jzqs.app.aftersale.service;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleListItemResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import java.util.List;
import java.util.Map;

public interface AftersaleService {
    List<AdminAftersaleListItemResponse> listCases(String status, String type, String serveDate);

    Map<String, Object> createCase(AdminAftersaleCreateRequest request);

    Map<String, Object> createMobileCase(long customerId, long orderId, MobileCreateAfterSaleRequest request);

    Map<String, Object> resolveCase(long caseId, AdminAftersaleResolveRequest request);

    List<MobileAfterSaleItemResponse> customerCases(long customerId);
}
