package com.jzqs.app.customer.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.customer.api.CustomerAddressActionResponse;
import com.jzqs.app.customer.api.CustomerAddressUpsertRequest;
import com.jzqs.app.customer.api.CustomerAssetResponse;
import com.jzqs.app.customer.api.CustomerDetailResponse;
import com.jzqs.app.customer.api.CustomerProfileCreateRequest;
import com.jzqs.app.customer.api.CustomerProfileCreateResponse;
import com.jzqs.app.customer.api.CustomerProfileUpdateRequest;
import com.jzqs.app.customer.api.CustomerProfileUpdateResponse;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
import com.jzqs.app.customer.api.CustomerWalletAdjustResponse;
import com.jzqs.app.customer.api.WalletAdjustRequest;
import com.jzqs.app.customer.api.WalletTransactionResponse;
import java.util.Map;

public interface CustomerAssetService {
    PageResponse<CustomerAssetResponse> listAssets(
        String keyword,
        String customerStatus,
        Boolean hasBalance,
        Boolean fixedSubscriptionEnabled,
        Boolean priorityCustomer
    );

    CustomerDetailResponse customerDetail(long customerId);

    CustomerProfileCreateResponse createCustomerProfile(CustomerProfileCreateRequest request);

    CustomerProfileUpdateResponse updateCustomerProfile(long customerId, CustomerProfileUpdateRequest request);

    CustomerAddressActionResponse createCustomerAddress(long customerId, CustomerAddressUpsertRequest request);

    CustomerAddressActionResponse updateCustomerAddress(long customerId, long addressId, CustomerAddressUpsertRequest request);

    CustomerAddressActionResponse deleteCustomerAddress(long customerId, long addressId);

    CustomerWalletAdjustResponse grantMeals(long customerId, WalletAdjustRequest request);

    CustomerWalletAdjustResponse deductMeals(long customerId, WalletAdjustRequest request);

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId);

    RemarkSuggestionResponse remarkSuggestions(String scene, Long customerId);
}
