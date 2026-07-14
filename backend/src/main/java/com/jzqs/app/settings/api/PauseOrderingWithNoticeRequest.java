package com.jzqs.app.settings.api;

import jakarta.validation.constraints.NotBlank;

public record PauseOrderingWithNoticeRequest(
    @NotBlank String title,
    @NotBlank String description,
    boolean popupEnabled,
    String popupContent
) {
}
