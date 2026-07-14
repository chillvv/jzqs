package com.jzqs.app.settings.api;

import java.util.List;

public record DispatchAreaMemoryListResponse(
    String areaCode,
    List<AreaMemoryItem> items
) {
    public record AreaMemoryItem(
        long id,
        String memoryType,
        String title,
        String summary,
        String applicableScene,
        int weight,
        String status,
        String updatedAt
    ) {
    }
}
