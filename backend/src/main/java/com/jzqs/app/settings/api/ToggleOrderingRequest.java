package com.jzqs.app.settings.api;
import jakarta.validation.constraints.NotNull;
public record ToggleOrderingRequest(@NotNull Boolean enabled) {
}
