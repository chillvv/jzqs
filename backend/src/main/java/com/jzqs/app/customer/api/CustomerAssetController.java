package com.jzqs.app.customer.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.common.aop.annotation.AuditAction;
import com.jzqs.app.common.aop.annotation.Idempotent;
import com.jzqs.app.common.aop.annotation.RateLimit;
import com.jzqs.app.common.security.AdminRequestContextSupport;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncRequest;
import com.jzqs.app.customer.service.CustomerMainSheetSyncService;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncSummaryResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
@RestController
@RequestMapping("/api/admin/customers")
public class CustomerAssetController {
    private final CustomerAssetService customerAssetService;
    private final CustomerMainSheetSyncService customerMainSheetSyncService;

    public CustomerAssetController(
        CustomerAssetService customerAssetService,
        CustomerMainSheetSyncService customerMainSheetSyncService
    ) {
        this.customerAssetService = customerAssetService;
        this.customerMainSheetSyncService = customerMainSheetSyncService;
    }

    @GetMapping("/assets")
    public ApiResponse<PageResponse<CustomerAssetResponse>> listAssets(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String customerStatus,
        @RequestParam(required = false) Boolean hasBalance,
        @RequestParam(required = false) Boolean fixedSubscriptionEnabled,
        @RequestParam(required = false) Boolean priorityCustomer
    ) {
        return ApiResponse.success(customerAssetService.listAssets(
            keyword,
            customerStatus,
            hasBalance,
            fixedSubscriptionEnabled,
            priorityCustomer
        ));
    }

    @GetMapping("/{customerId}")
    public ApiResponse<CustomerDetailResponse> detail(@PathVariable long customerId) {
        return ApiResponse.success(customerAssetService.customerDetail(customerId));
    }

    @PostMapping
    @RateLimit(key = "admin:customers:create-profile", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:customers:create-profile", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "CUSTOMER_ASSET", action = "CREATE_PROFILE")
    public ApiResponse<CustomerProfileCreateResponse> createProfile(@RequestBody CustomerProfileCreateRequest request) {
        return ApiResponse.success(customerAssetService.createCustomerProfile(request));
    }

    @PostMapping("/{customerId}/profile")
    @RateLimit(key = "admin:customers:update-profile", maxRequests = 4, windowSeconds = 10)
    @Idempotent(key = "admin:customers:update-profile", ttlSeconds = 8, includeBody = true)
    @AuditAction(module = "CUSTOMER_ASSET", action = "UPDATE_PROFILE")
    public ApiResponse<CustomerProfileUpdateResponse> updateProfile(
        @PathVariable long customerId,
        @RequestBody CustomerProfileUpdateRequest request
    ) {
        return ApiResponse.success(customerAssetService.updateCustomerProfile(customerId, request));
    }

    @PostMapping("/{customerId}/addresses")
    public ApiResponse<CustomerAddressActionResponse> createAddress(
        @PathVariable long customerId,
        @RequestBody CustomerAddressUpsertRequest request
    ) {
        return ApiResponse.success(customerAssetService.createCustomerAddress(customerId, request));
    }

    @PostMapping("/{customerId}/addresses/{addressId}")
    public ApiResponse<CustomerAddressActionResponse> updateAddress(
        @PathVariable long customerId,
        @PathVariable long addressId,
        @RequestBody CustomerAddressUpsertRequest request
    ) {
        return ApiResponse.success(customerAssetService.updateCustomerAddress(customerId, addressId, request));
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    public ApiResponse<CustomerAddressActionResponse> deleteAddress(@PathVariable long customerId, @PathVariable long addressId) {
        return ApiResponse.success(customerAssetService.deleteCustomerAddress(customerId, addressId));
    }

    @PostMapping("/{customerId}/wallet/grant")
    @RateLimit(key = "admin:customers:wallet:grant", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:customers:wallet:grant", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "CUSTOMER_ASSET", action = "WALLET_GRANT")
    public ApiResponse<CustomerWalletAdjustResponse> grant(@PathVariable long customerId, @Valid @RequestBody WalletAdjustRequest request) {
        WalletAdjustRequest normalizedRequest = new WalletAdjustRequest(
            request.mealDelta(),
            request.validityDays(),
            AdminRequestContextSupport.requireOperatorName(),
            request.remark()
        );
        return ApiResponse.success(customerAssetService.grantMeals(customerId, normalizedRequest));
    }

    @PostMapping("/{customerId}/wallet/deduct")
    @RateLimit(key = "admin:customers:wallet:deduct", maxRequests = 3, windowSeconds = 10)
    @Idempotent(key = "admin:customers:wallet:deduct", ttlSeconds = 5, includeBody = true)
    @AuditAction(module = "CUSTOMER_ASSET", action = "WALLET_DEDUCT")
    public ApiResponse<CustomerWalletAdjustResponse> deduct(@PathVariable long customerId, @Valid @RequestBody WalletAdjustRequest request) {
        WalletAdjustRequest normalizedRequest = new WalletAdjustRequest(
            request.mealDelta(),
            request.validityDays(),
            AdminRequestContextSupport.requireOperatorName(),
            request.remark()
        );
        return ApiResponse.success(customerAssetService.deductMeals(customerId, normalizedRequest));
    }

    @GetMapping("/{customerId}/wallet-transactions")
    public ApiResponse<PageResponse<WalletTransactionResponse>> transactions(@PathVariable long customerId) {
        return ApiResponse.success(customerAssetService.walletTransactions(customerId));
    }

    @GetMapping("/remark-suggestions")
    public ApiResponse<RemarkSuggestionResponse> remarkSuggestions(@RequestParam String scene, @RequestParam(required = false) Long customerId) {
        return ApiResponse.success(customerAssetService.remarkSuggestions(scene, customerId));
    }

    @PostMapping("/sync-main-sheet")
    @RateLimit(key = "admin:customers:sync-main-sheet", maxRequests = 2, windowSeconds = 30)
    @Idempotent(key = "admin:customers:sync-main-sheet", ttlSeconds = 15, includeBody = true)
    @AuditAction(module = "CUSTOMER_ASSET", action = "SYNC_MAIN_SHEET")
    public ApiResponse<CustomerMainSheetSyncSummaryResponse> syncMainSheet(@RequestBody CustomerMainSheetSyncRequest request) throws Exception {
        return ApiResponse.success(customerMainSheetSyncService.sync(request));
    }
}
