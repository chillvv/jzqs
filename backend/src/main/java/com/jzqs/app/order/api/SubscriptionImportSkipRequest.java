package com.jzqs.app.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubscriptionImportSkipRequest(
    @NotBlank(message = "配送日期不能为空") String serveDate,
    @NotEmpty(message = "请至少选择一条要跳过的记录") List<@Valid SubscriptionImportSkipItem> items
) {
}
