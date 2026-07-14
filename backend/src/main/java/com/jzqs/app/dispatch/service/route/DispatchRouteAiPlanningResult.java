package com.jzqs.app.dispatch.service.route;

import java.util.List;

public record DispatchRouteAiPlanningResult(
    boolean success,
    String summary,
    List<DispatchRouteAiStep> analysisSteps,
    List<DispatchRouteAiGroup> groups,
    List<Long> finalOrderIds,
    List<DispatchRouteAiOrderReason> perOrderReasons,
    double confidence,
    DispatchRouteAiFailure failure,
    String rawContent
) {
    public record DispatchRouteAiStep(
        String type,
        String title,
        String message
    ) {
    }

    public record DispatchRouteAiGroup(
        String groupName,
        List<Long> orderIds
    ) {
    }

    public record DispatchRouteAiOrderReason(
        long orderId,
        String reason
    ) {
    }

    public record DispatchRouteAiFailure(
        String code,
        String message,
        List<String> details
    ) {
    }
}
