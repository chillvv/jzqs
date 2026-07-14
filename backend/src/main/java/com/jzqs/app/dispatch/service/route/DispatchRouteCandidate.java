
package com.jzqs.app.dispatch.service.route;

public record DispatchRouteCandidate(
    long orderId,
    int suggestedSequence,
    double baseScore
) {}

