package com.jzqs.app.maintenance;

import java.util.List;

public record ExpiredReceiptFilesResponse(
    List<String> fileIds,
    int count,
    String cutoff
) {
}
