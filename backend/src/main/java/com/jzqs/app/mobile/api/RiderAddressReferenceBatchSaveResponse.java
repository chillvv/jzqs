package com.jzqs.app.mobile.api;

import java.util.List;

public record RiderAddressReferenceBatchSaveResponse(
    int updatedCount,
    List<Long> addressIds
) {
}
