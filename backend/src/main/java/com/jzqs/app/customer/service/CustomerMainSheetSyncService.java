package com.jzqs.app.customer.service;

import com.jzqs.app.customer.sync.CustomerMainSheetSyncRequest;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncSummaryResponse;

public interface CustomerMainSheetSyncService {
    CustomerMainSheetSyncSummaryResponse sync(CustomerMainSheetSyncRequest request) throws Exception;
}
