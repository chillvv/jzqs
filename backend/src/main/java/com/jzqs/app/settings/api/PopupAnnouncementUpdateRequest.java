package com.jzqs.app.settings.api;

public record PopupAnnouncementUpdateRequest(
    boolean enabled,
    String content
) {
}
