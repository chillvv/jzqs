package com.jzqs.app.dispatch.service.route;

import java.util.List;

public interface AreaMemoryModule {

    List<AreaMemoryItem> loadRoutingMemory(String areaCode, String scene);

    long recordCorrection(RecordCorrectionCommand command);

    MergeMemoryResult mergeMemory(long correctionId);

    record RecordCorrectionCommand(
        String areaCode,
        List<String> inputAddresses,
        List<Long> originalOrderIds,
        List<Long> merchantOrderIds,
        String merchantInstruction,
        String merchantReasonSummary,
        String confirmedBy
    ) {
    }

    record AreaMemoryItem(
        long id,
        String areaCode,
        String memoryType,
        String title,
        String summary,
        String applicableScene,
        int weight,
        String status,
        List<Long> sourceCorrectionIds
    ) {
    }

    record MergeMemoryResult(
        long memoryId,
        boolean created,
        String title,
        String summary
    ) {
    }
}
