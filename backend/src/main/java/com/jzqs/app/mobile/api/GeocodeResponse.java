package com.jzqs.app.mobile.api;

public record GeocodeResponse(
    double latitude,
    double longitude,
    String address,
    String formattedAddress,
    boolean fromCache,
    int confidence
) {
}
