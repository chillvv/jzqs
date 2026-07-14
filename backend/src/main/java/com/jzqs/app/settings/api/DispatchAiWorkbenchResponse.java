package com.jzqs.app.settings.api;

import java.util.List;

public record DispatchAiWorkbenchResponse(
    DispatchAiSettingsResponse settings,
    List<DispatchAiJobLogResponse> recentLogs
) {
}
