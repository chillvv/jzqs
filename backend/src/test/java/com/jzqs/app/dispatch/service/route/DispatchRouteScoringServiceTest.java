package com.jzqs.app.dispatch.service.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchRouteScoringServiceTest {

    private final DispatchRouteScoringService service = new DispatchRouteScoringService();

    @Test
    void shouldKeepSameClusterOrdersAdjacentInNearToFarMode() {
        List<DispatchRoutePoint> points = List.of(
            point(1L, "A苑1栋", 1.0, 1.0, "A苑", "1栋", "幸福路", 2.0, 3),
            point(2L, "A苑2栋", 1.2, 1.1, "A苑", "2栋", "幸福路", 2.2, 3),
            point(3L, "远点B", 8.0, 8.0, "B区", "1栋", "远航路", 12.0, 0)
        );

        List<DispatchRouteCandidate> ranked = service.rank(points, "NEAR_TO_FAR", 0.0, 0.0);

        assertEquals(List.of(1L, 2L, 3L), ranked.stream().map(DispatchRouteCandidate::orderId).toList());
    }

    @Test
    void shouldStartFromOuterClusterInFarToNearMode() {
        List<DispatchRoutePoint> points = List.of(
            point(10L, "近点", 1.0, 1.0, "近区", "1栋", "近路", 1.5, 0),
            point(11L, "远点1", 9.0, 9.0, "远区", "1栋", "外环路", 12.7, 2),
            point(12L, "远点2", 8.5, 8.8, "远区", "2栋", "外环路", 12.2, 2)
        );

        List<DispatchRouteCandidate> ranked = service.rank(points, "FAR_TO_NEAR", 0.0, 0.0);

        assertTrue(ranked.get(0).orderId() == 11L || ranked.get(0).orderId() == 12L);
        assertEquals(10L, ranked.get(ranked.size() - 1).orderId());
    }

    private DispatchRoutePoint point(
        long orderId,
        String address,
        double x,
        double y,
        String clusterName,
        String buildingName,
        String roadName,
        double anchorDistance,
        int neighborCount
    ) {
        return new DispatchRoutePoint(
            orderId,
            address,
            x,
            y,
            clusterName,
            buildingName,
            roadName,
            List.of(clusterName, roadName),
            anchorDistance,
            neighborCount
        );
    }
}
