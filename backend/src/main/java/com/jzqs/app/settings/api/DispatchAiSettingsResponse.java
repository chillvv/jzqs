package com.jzqs.app.settings.api;

public record DispatchAiSettingsResponse(
    boolean autoScheduleEnabled,
    String autoScheduleTime,
    String defaultStrategyMode,
    String anchorName,
    String anchorAddress,
    boolean aiEnabled,
    String apiBaseUrl,
    String maskedApiKey,
    String aiModel,
    String aiPromptTemplate,
    String balanceCurrency,
    boolean balanceAvailable,
    String totalBalance,
    String grantedBalance,
    String toppedUpBalance,
    String balanceCheckedAt,
    String lowBalanceThreshold
) {
}
