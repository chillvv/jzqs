
package com.jzqs.app.dispatch.service.route;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DispatchRouteScoringService {
    private static final double SAME_CLUSTER_BONUS = -2.0d;
    private static final double SAME_BUILDING_BONUS = -1.4d;
    private static final double SAME_ROAD_BONUS = -0.8d;
    private static final double NEIGHBOR_BONUS_FACTOR = -0.25d;
    private static final double FAR_TO_NEAR_WEIGHT = -0.55d;
    private static final double NEAR_TO_FAR_WEIGHT = 0.55d;
    private static final double U_TURN_PENALTY = 1.1d;
    private static final int LOCAL_OPTIMIZE_WINDOW = 4;

    public List<DispatchRouteCandidate> rank(
        List<DispatchRoutePoint> points,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        if (points.isEmpty()) {
            return List.of();
        }
        List<DispatchRoutePoint> orderedPoints = buildGreedyRoute(points, strategyMode, anchorX, anchorY);
        orderedPoints = optimizeLocal(orderedPoints, strategyMode, anchorX, anchorY);
        List<DispatchRouteCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < orderedPoints.size(); i++) {
            DispatchRoutePoint point = orderedPoints.get(i);
            double baseScore = calculateBaseScore(point, i, orderedPoints, strategyMode, anchorX, anchorY);
            candidates.add(new DispatchRouteCandidate(
                point.orderId(),
                i + 1,
                baseScore
            ));
        }

        return candidates;
    }

    private List<DispatchRoutePoint> buildGreedyRoute(
        List<DispatchRoutePoint> points,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        List<DispatchRoutePoint> remaining = new ArrayList<>(points);
        List<DispatchRoutePoint> result = new ArrayList<>();
        DispatchRoutePoint previous = anchorPoint(anchorX, anchorY);
        while (!remaining.isEmpty()) {
            DispatchRoutePoint next = chooseNext(previous, remaining, result, strategyMode, anchorX, anchorY);
            result.add(next);
            remaining.remove(next);
            previous = next;
        }
        return result;
    }

    private DispatchRoutePoint chooseNext(
        DispatchRoutePoint previous,
        List<DispatchRoutePoint> remaining,
        List<DispatchRoutePoint> chosen,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        if (chosen.isEmpty() && "FAR_TO_NEAR".equalsIgnoreCase(strategyMode)) {
            return chooseFarToNearStartingPoint(remaining);
        }
        DispatchRoutePoint best = remaining.get(0);
        double bestScore = Double.MAX_VALUE;
        for (DispatchRoutePoint candidate : remaining) {
            double score = candidateScore(previous, candidate, remaining, chosen, strategyMode, anchorX, anchorY);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private DispatchRoutePoint chooseFarToNearStartingPoint(List<DispatchRoutePoint> remaining) {
        DispatchRoutePoint best = remaining.get(0);
        for (int i = 1; i < remaining.size(); i++) {
            DispatchRoutePoint candidate = remaining.get(i);
            if (candidate.anchorDistance() > best.anchorDistance() + 0.001d) {
                best = candidate;
                continue;
            }
            if (Math.abs(candidate.anchorDistance() - best.anchorDistance()) <= 0.001d
                && candidate.neighborCount() > best.neighborCount()) {
                best = candidate;
            }
        }
        return best;
    }

    private List<DispatchRoutePoint> optimizeLocal(
        List<DispatchRoutePoint> points,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        if (points.size() <= 2) {
            return points;
        }
        List<DispatchRoutePoint> optimized = new ArrayList<>(points);
        boolean improved;
        do {
            improved = false;
            int startIndex = "FAR_TO_NEAR".equalsIgnoreCase(strategyMode) ? 1 : 0;
            for (int i = startIndex; i < optimized.size() - 2; i++) {
                int end = Math.min(optimized.size() - 1, i + LOCAL_OPTIMIZE_WINDOW - 1);
                double originalCost = pathCost(optimized, strategyMode, anchorX, anchorY);
                List<DispatchRoutePoint> candidate = new ArrayList<>(optimized);
                reverseSegment(candidate, i, end);
                compactClusterSegment(candidate, i, end);
                double newCost = pathCost(candidate, strategyMode, anchorX, anchorY);
                if (newCost + 0.001d < originalCost) {
                    optimized = candidate;
                    improved = true;
                }
            }
        } while (improved);
        return optimized;
    }

    private double pathCost(
        List<DispatchRoutePoint> points,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        double cost = 0;
        DispatchRoutePoint previous = anchorPoint(anchorX, anchorY);
        for (DispatchRoutePoint point : points) {
            cost += candidateScore(previous, point, points, List.of(), strategyMode, anchorX, anchorY);
            previous = point;
        }
        cost += distance(previous.x(), previous.y(), anchorX, anchorY);
        return cost;
    }

    private void reverseSegment(List<DispatchRoutePoint> list, int start, int end) {
        while (start < end) {
            DispatchRoutePoint temp = list.get(start);
            list.set(start, list.get(end));
            list.set(end, temp);
            start++;
            end--;
        }
    }

    private void compactClusterSegment(List<DispatchRoutePoint> points, int start, int end) {
        for (int i = start; i < end; i++) {
            DispatchRoutePoint current = points.get(i);
            DispatchRoutePoint next = points.get(i + 1);
            if (!sameCluster(current, next) && !current.clusterName().isBlank()) {
                int matchingIndex = findLaterClusterIndex(points, i + 2, end, current.clusterName());
                if (matchingIndex > 0) {
                    DispatchRoutePoint matching = points.remove(matchingIndex);
                    points.add(i + 1, matching);
                }
            }
        }
    }

    private int findLaterClusterIndex(List<DispatchRoutePoint> points, int start, int end, String clusterName) {
        for (int i = start; i <= end && i < points.size(); i++) {
            if (clusterName.equals(points.get(i).clusterName())) {
                return i;
            }
        }
        return -1;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double candidateScore(
        DispatchRoutePoint previous,
        DispatchRoutePoint candidate,
        List<DispatchRoutePoint> remaining,
        List<DispatchRoutePoint> chosen,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        double hopDistance = distance(previous.x(), previous.y(), candidate.x(), candidate.y());
        double anchorWeight = "FAR_TO_NEAR".equalsIgnoreCase(strategyMode) ? FAR_TO_NEAR_WEIGHT : NEAR_TO_FAR_WEIGHT;
        double anchorDirection = candidate.anchorDistance() * anchorWeight;
        double clusterBonus = sameCluster(previous, candidate) ? SAME_CLUSTER_BONUS : 0.0d;
        double buildingBonus = sameBuilding(previous, candidate) ? SAME_BUILDING_BONUS : 0.0d;
        double roadBonus = sameRoad(previous, candidate) ? SAME_ROAD_BONUS : 0.0d;
        double neighborBonus = candidate.neighborCount() * NEIGHBOR_BONUS_FACTOR;
        double futureCoverBonus = estimateFutureCoverBonus(candidate, remaining);
        double uTurnPenalty = estimateUTurnPenalty(previous, candidate, chosen, anchorX, anchorY);
        return hopDistance + anchorDirection + clusterBonus + buildingBonus + roadBonus + neighborBonus + futureCoverBonus + uTurnPenalty;
    }

    private double estimateFutureCoverBonus(DispatchRoutePoint candidate, List<DispatchRoutePoint> remaining) {
        int sameClusterCount = 0;
        int sameRoadCount = 0;
        for (DispatchRoutePoint point : remaining) {
            if (point.orderId() == candidate.orderId()) {
                continue;
            }
            if (sameCluster(candidate, point)) {
                sameClusterCount++;
            } else if (sameRoad(candidate, point)) {
                sameRoadCount++;
            }
        }
        return sameClusterCount * -0.45d + sameRoadCount * -0.2d;
    }

    private double estimateUTurnPenalty(
        DispatchRoutePoint previous,
        DispatchRoutePoint candidate,
        List<DispatchRoutePoint> chosen,
        double anchorX,
        double anchorY
    ) {
        if (chosen.isEmpty()) {
            return 0.0d;
        }
        DispatchRoutePoint last = chosen.get(chosen.size() - 1);
        double previousAngle = Math.atan2(last.y() - anchorY, last.x() - anchorX);
        double candidateAngle = Math.atan2(candidate.y() - anchorY, candidate.x() - anchorX);
        double angleDiff = Math.abs(previousAngle - candidateAngle);
        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }
        return angleDiff > 2.2d ? U_TURN_PENALTY : 0.0d;
    }

    private double calculateBaseScore(
        DispatchRoutePoint point,
        int index,
        List<DispatchRoutePoint> orderedPoints,
        String strategyMode,
        double anchorX,
        double anchorY
    ) {
        double anchorPreference = "FAR_TO_NEAR".equalsIgnoreCase(strategyMode)
            ? point.anchorDistance()
            : 1.0d / (1.0d + point.anchorDistance());
        double clusterScore = 0.0d;
        if (index > 0 && sameCluster(point, orderedPoints.get(index - 1))) {
            clusterScore += 0.5d;
        }
        if (index > 0 && sameBuilding(point, orderedPoints.get(index - 1))) {
            clusterScore += 0.35d;
        }
        double neighborScore = Math.min(1.0d, point.neighborCount() / 5.0d);
        double rawScore = anchorPreference + clusterScore + neighborScore;
        return BigDecimal.valueOf(rawScore)
            .setScale(4, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private boolean sameCluster(DispatchRoutePoint left, DispatchRoutePoint right) {
        return !left.clusterName().isBlank() && left.clusterName().equals(right.clusterName());
    }

    private boolean sameBuilding(DispatchRoutePoint left, DispatchRoutePoint right) {
        return !left.buildingName().isBlank() && left.buildingName().equals(right.buildingName());
    }

    private boolean sameRoad(DispatchRoutePoint left, DispatchRoutePoint right) {
        return !left.roadName().isBlank() && left.roadName().equals(right.roadName());
    }

    private DispatchRoutePoint anchorPoint(double anchorX, double anchorY) {
        return new DispatchRoutePoint(-1L, "ANCHOR", anchorX, anchorY, "", "", "", List.of(), 0.0d, 0);
    }
}
