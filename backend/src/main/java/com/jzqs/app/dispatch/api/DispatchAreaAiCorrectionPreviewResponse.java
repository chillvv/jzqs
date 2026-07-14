package com.jzqs.app.dispatch.api;

import java.util.List;

public record DispatchAreaAiCorrectionPreviewResponse(
    long correctionId,
    String aiInterpretationSummary,
    String replanStatus,
    String replanError,
    List<Long> finalOrderIds,
    List<MemoryCandidateItem> memoryCandidates
) {
    public record MemoryCandidateItem(
        String memoryType,
        String title,
        String summary
    ) {
    }
}
