package com.jzqs.app.mobile.api;

public record RiderAddressReferenceReplaceResponse(
    long addressId,
    String referenceImageUrl,
    boolean updated
) {
}
