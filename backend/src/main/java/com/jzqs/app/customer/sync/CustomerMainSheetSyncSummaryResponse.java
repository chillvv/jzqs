package com.jzqs.app.customer.sync;

public record CustomerMainSheetSyncSummaryResponse(
    int importedCount,
    int skippedCount,
    int walletCount,
    String filePath
) {
}
