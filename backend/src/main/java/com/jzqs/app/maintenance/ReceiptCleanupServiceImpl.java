package com.jzqs.app.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReceiptCleanupServiceImpl implements ReceiptCleanupService {
    private final JdbcTemplate jdbcTemplate;

    public ReceiptCleanupServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExpiredReceiptFilesResponse getExpiredFileIds() {
        LocalDateTime cutoff = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<String> fileIds = jdbcTemplate.queryForList(
            """
            SELECT receipt_url
            FROM delivery_receipts
            WHERE delivered_at < ?
              AND receipt_url LIKE 'cloud://%'
              AND (cloud_deleted IS NULL OR cloud_deleted = FALSE)
            LIMIT 500
            """,
            String.class,
            cutoff
        );
        return new ExpiredReceiptFilesResponse(fileIds, fileIds.size(), cutoff.toString());
    }

    @Override
    public MarkCloudDeletedResponse markCloudDeleted(MarkCloudDeletedRequest request) {
        List<String> fileIds = request == null ? null : request.fileIds();
        if (fileIds == null || fileIds.isEmpty()) {
            return new MarkCloudDeletedResponse(0, 0);
        }

        int updated = 0;
        for (String fileId : fileIds) {
            updated += jdbcTemplate.update(
                "UPDATE delivery_receipts SET cloud_deleted = TRUE WHERE receipt_url = ?",
                fileId
            );
        }
        return new MarkCloudDeletedResponse(updated, fileIds.size());
    }
}
