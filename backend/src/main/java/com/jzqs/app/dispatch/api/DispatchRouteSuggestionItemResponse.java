
package com.jzqs.app.dispatch.api;

public record DispatchRouteSuggestionItemResponse(
    long orderId,
    int suggestedSequence,
    boolean aiAdjusted,
    int ruleSequence,
    String addressLabel,
    String clusterName,
    String buildingName,
    String roadName,
    String distanceBand,
    int neighborCount,
    String adjustmentReason
) {}
