package com.jzqs.app.order.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchConsumeOrdersRequest(@NotEmpty List<Long> orderIds) {
}
