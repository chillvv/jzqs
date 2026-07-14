package com.jzqs.app.settings.api;

public record PopupAnnouncementUpdateRequest(
    String title,
    String description,
    boolean enabled,
    String content
) {
}
