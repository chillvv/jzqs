package com.jzqs.app.rider.model.dto;

public record RiderQueryRequest(
    Integer page,
    Integer size,
    String keyword,
    String authStatus,
    String employmentStatus
) {
    public long pageNoOrDefault() {
        if (page == null || page < 1) return 1L;
        return page;
    }

    public long pageSizeOrDefault() {
        if (size == null || size < 1) return 20L;
        return Math.min(size, 100);
    }
}
