package com.jzqs.app.customer.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
public record CustomerNoteUpsertRequest(
    @NotBlank String noteType,
    @NotNull String scopeType,
    @NotBlank @Size(max = 255) String content,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer displayOrder
) {
}
