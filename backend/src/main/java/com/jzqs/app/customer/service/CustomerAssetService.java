package com.jzqs.app.customer.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.customer.api.CustomerAssetResponse;
import com.jzqs.app.customer.api.CustomerNoteUpsertRequest;
import com.jzqs.app.customer.api.CustomerNotesResponse;
import com.jzqs.app.customer.api.RemarkSuggestionResponse;
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

    java.util.Map<String, Object> customerDetail(long customerId);

    CustomerNotesResponse customerNotes(long customerId);

    Map<String, Object> saveCustomerNote(long customerId, CustomerNoteUpsertRequest request);

    Map<String, Object> createCustomerProfile(Map<String, Object> payload);

    Map<String, Object> updateCustomerProfile(long customerId, Map<String, Object> payload);

    Map<String, Object> grantMeals(long customerId, WalletAdjustRequest request);

    Map<String, Object> deductMeals(long customerId, WalletAdjustRequest request);

    PageResponse<WalletTransactionResponse> walletTransactions(long customerId);

    RemarkSuggestionResponse remarkSuggestions(String scene, Long customerId);
}
