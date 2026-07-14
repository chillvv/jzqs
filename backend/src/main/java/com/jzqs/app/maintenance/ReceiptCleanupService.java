package com.jzqs.app.maintenance;

public interface ReceiptCleanupService {
    ExpiredReceiptFilesResponse getExpiredFileIds();

    MarkCloudDeletedResponse markCloudDeleted(MarkCloudDeletedRequest request);
}
