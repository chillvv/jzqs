package com.jzqs.app.aftersale.service;

import com.jzqs.app.aftersale.api.AdminAftersaleCreateRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleCreateResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleListItemResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleOrderOptionResponse;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveRequest;
import com.jzqs.app.aftersale.api.AdminAftersaleResolveResponse;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleResponse;
import java.util.List;

public interface AftersaleService {
    List<AdminAftersaleListItemResponse> listCases(
        String status,
        String type,
        String startDate,
        String endDate,
        String view,
        Boolean hideAutoRefund
    );

    List<AdminAftersaleOrderOptionResponse> orderOptions(String serveDate);

    AdminAftersaleCreateResponse createCase(AdminAftersaleCreateRequest request);

    MobileCreateAfterSaleResponse createMobileCase(long customerId, long orderId, MobileCreateAfterSaleRequest request);

    AdminAftersaleResolveResponse resolveCase(long caseId, AdminAftersaleResolveRequest request);

    List<MobileAfterSaleItemResponse> customerCases(long customerId);
}
