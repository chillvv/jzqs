package com.jzqs.app.mobile.api;

public record RiderDeliveryExceptionReportResponse(
    long exceptionId,
    String status,
    String message
) {
}
