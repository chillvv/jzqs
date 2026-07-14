package com.jzqs.app.analysis.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCostEntryRequest(
    @NotNull(message = "成本日期不能为空")
    LocalDate costDate,
    @NotBlank(message = "成本分类不能为空")
    String costCategory,
    @NotNull(message = "成本金额不能为空")
    @DecimalMin(value = "0.01", message = "成本金额必须大于 0")
    BigDecimal amount,
    String remark,
    String recordedBy
) {}
