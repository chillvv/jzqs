
package com.jzqs.app.dispatch.service.route;

import java.util.List;

public record DispatchRoutePoint(
    long orderId,
    String addressLabel,
    double x,
    double y,
    String clusterName,
    String buildingName,
    String roadName,
    List<String> locationTokens,
    double anchorDistance,
    int neighborCount
) {}
