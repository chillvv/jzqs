package com.jzqs.app.order.api;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record ConfirmPreviewRequest(@NotEmpty List<Long> dailyOrderIds) {
}
