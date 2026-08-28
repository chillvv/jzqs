package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.api.CustomerProfileUpdateRequest;
import com.jzqs.app.customer.api.CustomerProfileUpdateResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import com.jzqs.app.mobile.MobilePortalService;
import com.jzqs.app.mobile.api.MobileAfterSaleItemResponse;
import com.jzqs.app.mobile.api.MobileCreateAfterSaleRequest;
import com.jzqs.app.order.api.OrderActionResponse;
import com.jzqs.app.subscription.service.SubscriptionRuleService;
import com.jzqs.app.mobile.api.MobileSubscriptionRuleRequest;
import com.jzqs.app.mobile.api.MobileSubscriptionRuleResponse;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/customer")
public class MobileCustomerController {
    private final MobilePortalService mobilePortalService;
    private final CustomerAssetService customerAssetService;
    private final SubscriptionRuleService subscriptionRuleService;

    public MobileCustomerController(
        MobilePortalService mobilePortalService,
        CustomerAssetService customerAssetService,
        SubscriptionRuleService subscriptionRuleService
    ) {
        this.mobilePortalService = mobilePortalService;
        this.customerAssetService = customerAssetService;
        this.subscriptionRuleService = subscriptionRuleService;
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

    @GetMapping("/menu/next-week")
    public ApiResponse<MobileCurrentWeekResponse> nextWeekMenu() {
        return ApiResponse.success(mobilePortalService.nextWeekMenu());
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

    // 下单幂等窗口 300s：覆盖"前端报错/超时后用户重试"的真实间隔(原10s过短，
    // 用户第一轮已成功但提示繁忙,重试间隔>10s即绕过幂等造成同餐次数量叠加翻倍)。
    // includeBody=true：同一用户会同时并发下"午餐+晚餐"两个 POST /orders(Promise.all)，
    // 若不区分请求体，两者幂等 key 完全相同，后到的请求被误判"重复提交"报错，导致
    // 前端提示系统繁忙、只成功 1 个餐次且不显示"预订成功"。含 body 后两餐次 key 不同
    // 互不拦截。
    // clientRequestId（前端每次下单生成的唯一 ID）进 body：区分「有意加餐」与「同一
    // 操作的重试」——相同业务参数的有意加餐因新 ID 不命中幂等，正常走合并加餐；同一次
    // 操作重试复用同一 ID 仍被拦截，防重复扣餐能力不变。
    @Idempotent(key = "order:create", ttlSeconds = 300, includeBody = true)
    @PostMapping("/orders")
    public ApiResponse<MobileCreateOrderResponse> createOrder(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileCreateOrderRequest request
    ) {
        long customerId = extractCustomerId(authorization);
        MobileCreateOrderResponse response = mobilePortalService.createMiniappOrder(
            customerId,
            request.serveDate(),
            request.mealPeriod(),
            request.deliveryAddress(),
            request.note(),
            request.quantityOrDefault()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/delivery-subscription")
    public ApiResponse<MobileDeliverySubscriptionAuthorizeResponse> authorizeDeliverySubscription(
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

    @PostMapping("/nightly-subscription")
    public ApiResponse<MobileDeliverySubscriptionAuthorizeResponse> authorizeNightlySubscription(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileDeliverySubscriptionRequest request
    ) {
        return ApiResponse.success(
            mobilePortalService.authorizeNightlySubscription(
                extractCustomerId(authorization),
                request.templateId(),
                request.acceptResult()
            )
        );
    }

    @GetMapping("/nightly-subscription/status")
    public ApiResponse<NightlySubscriptionStatusResponse> nightlySubscriptionStatus(
        @RequestHeader("Authorization") String authorization
    ) {
        long customerId = extractCustomerId(authorization);
        return ApiResponse.success(
            new NightlySubscriptionStatusResponse(mobilePortalService.isNightlySubscribed(customerId))
        );
    }

    @DeleteMapping("/nightly-subscription")
    public ApiResponse<Void> cancelNightlySubscription(
        @RequestHeader("Authorization") String authorization
    ) {
        mobilePortalService.cancelNightlySubscription(extractCustomerId(authorization));
        return ApiResponse.success(null);
    }

    @PutMapping("/nightly-subscription/sync")
    public ApiResponse<Void> syncNightlySubscription(
        @RequestHeader("Authorization") String authorization,
        @RequestBody NightlySubscriptionSyncRequest request
    ) {
        mobilePortalService.syncNightlySubscription(
            extractCustomerId(authorization),
            request.enabled(),
            request.templateId()
        );
        return ApiResponse.success(null);
    }

    @PostMapping("/subscribe-message/test-send")
    public ApiResponse<MobileSubscribeMessageTestResponse> sendSubscribeMessageTest(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileSubscribeMessageTestRequest request
    ) {
        return ApiResponse.success(
            mobilePortalService.sendSubscribeMessageTest(
                extractCustomerId(authorization),
                request.templateId(),
                request.acceptResult(),
                request.type()
            )
        );
    }

    @Idempotent(key = "order:cancel", ttlSeconds = 300)
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<OrderActionResponse> cancelOrder(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization
    ) {
        long customerId = extractCustomerId(authorization);
        OrderActionResponse response = mobilePortalService.cancelMiniappOrder(customerId, orderId);
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/change-address")
    public ApiResponse<MobileOrderAddressChangeResponse> changeOrderAddress(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileOrderAddressChangeRequest request
    ) {
        long customerId = extractCustomerId(authorization);
        MobileOrderAddressChangeResponse response = mobilePortalService.changeCustomerOrderAddress(
            customerId,
            orderId,
            request.addressId()
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/orders/{orderId}/delete")
    public ApiResponse<OrderActionResponse> deleteOrder(
        @PathVariable long orderId,
        @RequestHeader("Authorization") String authorization
    ) {
        long customerId = extractCustomerId(authorization);
        OrderActionResponse response = mobilePortalService.deleteMiniappOrder(customerId, orderId);
        return ApiResponse.success(response);
    }

    @Idempotent(key = "aftersale:create", ttlSeconds = 300)
    @PostMapping("/orders/{orderId}/after-sales")
    public ApiResponse<MobileCreateAfterSaleResponse> createAfterSale(
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

    @Idempotent(key = "address:save", ttlSeconds = 10)
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

    @Idempotent(key = "address:update", ttlSeconds = 10)
    @PutMapping("/addresses/{addressId}")
    public ApiResponse<MobileAddressResponse> updateAddress(
        @PathVariable long addressId,
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody MobileAddressUpsertRequest request
    ) {
        return ApiResponse.success(mobilePortalService.updateCustomerAddress(
            extractCustomerId(authorization),
            addressId,
            request.contactName(),
            request.contactPhone(),
            request.addressLine(),
            request.areaCode(),
            request.isDefault()
        ));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<Void> deleteAddress(
        @PathVariable long addressId,
        @RequestHeader("Authorization") String authorization
    ) {
        mobilePortalService.deleteCustomerAddress(extractCustomerId(authorization), addressId);
        return ApiResponse.success(null);
    }

    @PostMapping("/addresses/{addressId}/default")
    public ApiResponse<MobileDefaultAddressResponse> setDefaultAddress(
        @PathVariable long addressId,
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.setDefaultAddress(extractCustomerId(authorization), addressId));
    }

    @GetMapping("/subscription-rule")
    public ApiResponse<MobileSubscriptionRuleResponse> getSubscriptionRule(@RequestAttribute("customerId") Long customerId) {
        return ApiResponse.success(subscriptionRuleService.getRuleByCustomerId(customerId));
    }

    // 修复：幂等键需包含请求体（includeBody=true）。否则暂停(enabled=false)后立即恢复(enabled=true)
    //       会命中相同 key_hash 被误判为"重复提交"，恢复订阅被 REPEAT_SUBMISSION 拦截。
    @Idempotent(key = "subscription:update", ttlSeconds = 10, includeBody = true)
    @PostMapping("/subscription-rule")
    public ApiResponse<MobileSubscriptionRuleResponse> updateSubscriptionRule(
        @RequestAttribute("customerId") Long customerId,
        @Valid @RequestBody MobileSubscriptionRuleRequest request
    ) {
        return ApiResponse.success(subscriptionRuleService.updateRuleByCustomer(customerId, request));
    }

    @GetMapping("/wallet-transactions")
    public ApiResponse<PageResponse<WalletTransactionResponse>> walletTransactions(
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.walletTransactions(extractCustomerId(authorization)));
    }

    @GetMapping("/wallet/balance")
    public ApiResponse<MobileWalletBalanceResponse> walletBalance(
        @RequestHeader("Authorization") String authorization
    ) {
        return ApiResponse.success(mobilePortalService.walletBalance(extractCustomerId(authorization)));
    }

    @Idempotent(key = "profile:update", ttlSeconds = 10)
    @PostMapping("/profile")
    public ApiResponse<CustomerProfileUpdateResponse> updateProfile(
        @RequestHeader("Authorization") String authorization,
        @RequestBody CustomerProfileUpdateRequest request
    ) {
        return ApiResponse.success(customerAssetService.updateCustomerProfile(extractCustomerId(authorization), request));
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
