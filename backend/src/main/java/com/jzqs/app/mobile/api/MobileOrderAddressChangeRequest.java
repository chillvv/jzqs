package com.jzqs.app.mobile.api;

import jakarta.validation.constraints.NotNull;

public record MobileOrderAddressChangeRequest(
    @NotNull Long addressId
) {
}
