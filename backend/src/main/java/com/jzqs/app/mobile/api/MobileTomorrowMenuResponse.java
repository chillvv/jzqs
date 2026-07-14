package com.jzqs.app.mobile.api;

public record MobileTomorrowMenuResponse(
    String serveDate,
    boolean selfOrderEnabled,
    String selfOrderNotice,
    MobileMenuItemResponse lunchItem,
    MobileMenuItemResponse dinnerItem,
    boolean canOrder,
    String statusText
) {
}
