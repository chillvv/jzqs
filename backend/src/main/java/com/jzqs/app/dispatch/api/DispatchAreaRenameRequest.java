package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchAreaRenameRequest(
    @NotBlank(message = "newAreaCode is required") String newAreaCode
) {
}
