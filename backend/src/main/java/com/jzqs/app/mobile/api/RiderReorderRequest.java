package com.jzqs.app.mobile.api;

import java.util.List;

public record RiderReorderRequest(
    List<Long> batchItemIds
) {
}
