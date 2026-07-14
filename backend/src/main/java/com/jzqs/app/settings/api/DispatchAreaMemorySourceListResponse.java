package com.jzqs.app.settings.api;

import java.util.List;

public record DispatchAreaMemorySourceListResponse(
    String areaCode,
    long memoryId,
    String memoryTitle,
    List<MemorySourceItem> items
) {
    public record MemorySourceItem(
        long correctionId,
        String correctionMode,
        String merchantInstruction,
        String merchantReasonSummary,
        String aiInterpretationSummary,
        String replanStatus,
        String confirmedAt
    ) {
    }
}
