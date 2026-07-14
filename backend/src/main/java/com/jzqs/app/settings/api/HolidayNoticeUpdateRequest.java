package com.jzqs.app.settings.api;
import jakarta.validation.constraints.NotBlank;
public record HolidayNoticeUpdateRequest(
    @NotBlank String title,
    @NotBlank String description
) {
}
