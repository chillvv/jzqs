package com.jzqs.app.user.model.dto;

public record UserQueryRequest(
    Integer page,
    Integer size,
    String keyword,
    String status
) {
    public long pageNoOrDefault() {
        if (page == null || page < 1) {
            return 1L;
        }
        return page;
    }

    public long pageSizeOrDefault() {
        if (size == null || size < 1) {
            return 20L;
        }
        return Math.min(size, 100);
    }
}
