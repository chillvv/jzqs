package com.jzqs.app.aftersale.api;

import jakarta.validation.constraints.NotBlank;

public record AdminAftersaleResolveRequest(
    @NotBlank String resolutionAction,
    boolean refundBlocking,
    int walletDelta,
    String adminRemark,
    String operatorName
) {
}
