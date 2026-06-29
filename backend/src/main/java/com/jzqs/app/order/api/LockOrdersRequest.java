package com.jzqs.app.order.api;
import jakarta.validation.constraints.NotBlank;
public record LockOrdersRequest(@NotBlank String serveDate) {
}
