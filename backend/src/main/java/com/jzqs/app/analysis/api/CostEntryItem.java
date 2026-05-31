package com.jzqs.app.analysis.api;

import java.math.BigDecimal;

public record CostEntryItem(
    long id,
    String costDate,
    String costCategory,
    BigDecimal amount,
    String remark,
    String recordedBy
) {
}
