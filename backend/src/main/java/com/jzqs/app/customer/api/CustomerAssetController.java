package com.jzqs.app.customer.api;
import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
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
    public ApiResponse<Map<String, Object>> detail(@PathVariable long customerId) {
        return ApiResponse.success(customerAssetService.customerDetail(customerId));
    }

    @GetMapping("/{customerId}/notes")
    public ApiResponse<CustomerNotesResponse> customerNotes(@PathVariable long customerId) {
        return ApiResponse.success(customerAssetService.customerNotes(customerId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(customerAssetService.createCustomerProfile(body));
    }

    @PostMapping("/{customerId}/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@PathVariable long customerId, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(customerAssetService.updateCustomerProfile(customerId, body));
    }

    @PostMapping("/{customerId}/notes")
    public ApiResponse<Map<String, Object>> saveCustomerNote(
        @PathVariable long customerId,
        @Valid @RequestBody CustomerNoteUpsertRequest request
    ) {
        return ApiResponse.success(customerAssetService.saveCustomerNote(customerId, request));
    }

    @PostMapping("/{customerId}/wallet/grant")
    public ApiResponse<Map<String, Object>> grant(@PathVariable long customerId, @Valid @RequestBody WalletAdjustRequest request) {
        return ApiResponse.success(customerAssetService.grantMeals(customerId, request));
    }

    @PostMapping("/{customerId}/wallet/deduct")
    public ApiResponse<Map<String, Object>> deduct(@PathVariable long customerId, @Valid @RequestBody WalletAdjustRequest request) {
        return ApiResponse.success(customerAssetService.deductMeals(customerId, request));
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
    public ApiResponse<CustomerMainSheetSyncSummaryResponse> syncMainSheet(@RequestBody CustomerMainSheetSyncRequest request) throws Exception {
        return ApiResponse.success(customerMainSheetSyncService.sync(request));
    }
}
