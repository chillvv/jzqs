package com.jzqs.app.order.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record OrderNoteCreateRequest(
    @NotBlank String noteType,
    @NotNull String scopeType,
    @NotBlank @Size(max = 255) String content
) {
}
