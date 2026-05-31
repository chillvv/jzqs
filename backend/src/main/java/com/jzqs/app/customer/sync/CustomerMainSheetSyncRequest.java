package com.jzqs.app.customer.sync;

public record CustomerMainSheetSyncRequest(
    boolean clearExisting,
    String filePath
) {
}
