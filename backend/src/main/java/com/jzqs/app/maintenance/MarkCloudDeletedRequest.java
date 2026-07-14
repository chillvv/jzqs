package com.jzqs.app.maintenance;

import java.util.List;

public record MarkCloudDeletedRequest(
    List<String> fileIds
) {
}
