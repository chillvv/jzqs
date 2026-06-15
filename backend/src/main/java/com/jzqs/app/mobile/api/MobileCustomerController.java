package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.MobilePortalService;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/customer")
public class MobileCustomerController {
    private final MobilePortalService mobilePortalService;
    private final CustomerAssetService customerAssetService;

    public MobileCustomerController(
        MobilePortalService mobilePortalService,
        CustomerAssetService customerAssetService
    ) {
        this.mobilePortalService = mobilePortalService;
        this.customerAssetService = customerAssetService;
    }

    @GetMapping("/home")
    public ApiResponse<MobileHomeResponse> home(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long customerId = extractOptionalCustomerId(authorization);
        if (customerId == null) {
            return ApiResponse.success(mobilePortalService.guestHome());
        }
        return ApiResponse.success(mobilePortalService.customerHome(customerId));
    }

    @GetMapping("/menus/week")
    public ApiResponse<List<MobileWeekMenuDayResponse>> weekMenus(@RequestParam String startDate) {
        return ApiResponse.success(mobilePortalService.weekMenus(startDate));
    }

    @GetMapping("/menu/current-week")
    public ApiResponse<MobileCurrentWeekResponse> currentWeekMenu() {
        return ApiResponse.success(mobilePortalService.currentWeekMenu());
    }

    @GetMapping("/menu/tomorrow")
    public ApiResponse<MobileTomorrowMenuResponse> tomorrowMenu() {
        return ApiResponse.success(mobilePortalService.tomorrowMenu());
    }

    @GetMapping("/menu")
    public ApiResponse<PageResponse<MobileMenuItemResponse>> menu(@RequestParam String serveDate) {
        return ApiResponse.success(mobilePortalService.publishedMenus(serveDate));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<MobileOrderItemResponse>> orders(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(mobilePortalService.customerOrders(extractCustomerId(authorization), status));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileCreateOrderRequest request
    ) {
        long customerId = extractCustomerId(authorization);
        Map<String, Object> response = mobilePortalService.createMiniappOrder(
            customerId,
            request.serveDate(),
            request.mealPeriod(),
            request.deliveryAddress(),
            request.note()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/delivery-subscription")
    public ApiResponse<Map<String, Object>> authorizeDeliverySubscription(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileDeliverySubscriptionRequest request
    ) {
        return ApiResponse.success(
            mobilePortalService.authorizeDeliverySubscription(
                extractCustomerId(authorization),
                orderId,
                request.templateId(),
                request.acceptResult()
            )
        );
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancelOrder(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization
    ) {
        long customerId = extractCustomerId(authorization);
        Map<String, Object> response = mobilePortalService.cancelMiniappOrder(customerId, orderId);
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/change-address")
    public ApiResponse<Map<String, Object>> changeOrderAddress(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileOrderAddressChangeRequest request
    ) {
        long customerId = extractCustomerId(authorization);
        Map<String, Object> response = mobilePortalService.changeCustomerOrderAddress(
            customerId,
            orderId,
            request.addressId()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/delete")
    public ApiResponse<Map<String, Object>> deleteOrder(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization
    ) {
        long customerId = extractCustomerId(authorization);
        Map<String, Object> response = mobilePortalService.deleteMiniappOrder(customerId, orderId);
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/after-sales")
    public ApiResponse<Map<String, Object>> createAfterSale(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileCreateAfterSaleRequest request
    ) {
        return ApiResponse.success(
            mobilePortalService.createAfterSale(extractCustomerId(authorization), orderId, request)
        );
    }

    @GetMapping("/after-sales")
    public ApiResponse<List<MobileAfterSaleItemResponse>> afterSales(
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.customerAfterSales(extractCustomerId(authorization)));
    }

    @GetMapping("/remark-suggestions")
    public ApiResponse<RemarkSuggestionResponse> remarkSuggestions(
        @RequestHeader("Authorization") String authorization,
        @RequestParam String scene
    ) {
        long customerId = extractCustomerId(authorization);
        return ApiResponse.success(customerAssetService.remarkSuggestions(scene, customerId));
    }

    @GetMapping("/addresses")
    public ApiResponse<List<MobileAddressResponse>> addresses(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(mobilePortalService.customerAddresses(extractCustomerId(authorization)));
    }

    @PostMapping("/addresses")
    public ApiResponse<MobileAddressResponse> saveAddress(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileAddressUpsertRequest request
    ) {
        return ApiResponse.success(mobilePortalService.saveCustomerAddress(
            extractCustomerId(authorization),
            request.contactName(),
            request.contactPhone(),
            request.addressLine(),
            request.areaCode(),
            request.isDefault()
        ));
    }

    @PostMapping("/addresses/{addressId}/default")
    public ApiResponse<Map<String, Object>> setDefaultAddress(
        @PathVariable long addressId,
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.setDefaultAddress(extractCustomerId(authorization), addressId));
    }

    @GetMapping("/wallet-transactions")
    public ApiResponse<PageResponse<WalletTransactionResponse>> walletTransactions(
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.walletTransactions(extractCustomerId(authorization)));
    }

    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(
        @RequestHeader("Authorization") String authorization,
        @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.success(customerAssetService.updateCustomerProfile(extractCustomerId(authorization), body));
    }

    private long extractCustomerId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少登录凭证");
        }
        return JwtUtils.parseCustomerId(authorization.substring("Bearer ".length()).trim());
    }

    private Long extractOptionalCustomerId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return extractCustomerId(authorization);
    }
}
