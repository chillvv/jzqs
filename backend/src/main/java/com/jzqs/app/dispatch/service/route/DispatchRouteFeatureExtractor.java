package com.jzqs.app.dispatch.service.route;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DispatchRouteFeatureExtractor {
    private static final Pattern CONTACT_PREFIX = Pattern.compile("^[^-]{1,16}-1\\d{10}-");
    private static final Pattern CLUSTER_PATTERN = Pattern.compile(
        "([\\p{IsHan}A-Za-z0-9]{2,24}?(?:软件园|科技园|产业园|工业园|商务区|商圈|广场|中心|大厦|公寓|花园|新城|国际|天地|校区|学院|大学|酒店|小区|社区|家园|府|城|苑|园|里|厦|湾|庭|寓))"
    );
    private static final Pattern ROAD_PATTERN = Pattern.compile(
        "([\\p{IsHan}A-Za-z0-9]{2,20}?(?:路|街|大道|巷|道))"
    );
    private static final Pattern BUILDING_PATTERN = Pattern.compile(
        "([A-Za-z]?[0-9一二三四五六七八九十甲乙丙丁]{1,4}(?:号楼|栋|幢|座|楼|舍|期|区|单元))"
    );
    private static final List<String> GENERIC_PREFIXES = List.of(
        "湖北省",
        "武汉市",
        "洪山区",
        "江夏区",
        "武昌区",
        "东西湖区",
        "东湖高新区",
        "关东街道",
        "街道",
        "路街道"
    );

    public List<DispatchRoutePoint> extractAll(List<RouteAddressSeed> seeds, String areaCode, double anchorX, double anchorY) {
        List<DispatchRoutePoint> points = new ArrayList<>();
        for (RouteAddressSeed seed : seeds) {
            points.add(extract(seed.orderId(), seed.addressLabel(), areaCode, anchorX, anchorY));
        }
        return enrichNeighborCounts(points);
    }

    public DispatchRoutePoint extract(long orderId, String addressLabel, String areaCode, double anchorX, double anchorY) {
        String normalized = normalizeAddress(addressLabel);
        String clusterKey = extractClusterKey(normalized, areaCode);
        String roadKey = extractRoadKey(normalized);
        String buildingKey = extractBuildingKey(normalized);
        List<String> locationTokens = buildLocationTokens(normalized, clusterKey, roadKey, buildingKey);
        Point point = estimatePoint(normalized, areaCode, clusterKey, roadKey, buildingKey);
        double anchorDistance = Math.hypot(point.x() - anchorX, point.y() - anchorY);
        return new DispatchRoutePoint(
            orderId,
            normalized.isBlank() ? safeString(addressLabel) : normalized,
            point.x(),
            point.y(),
            clusterKey,
            buildingKey,
            roadKey,
            List.copyOf(locationTokens),
            anchorDistance,
            0
        );
    }

    private List<DispatchRoutePoint> enrichNeighborCounts(List<DispatchRoutePoint> points) {
        List<DispatchRoutePoint> enriched = new ArrayList<>();
        for (DispatchRoutePoint point : points) {
            int neighborCount = 0;
            for (DispatchRoutePoint candidate : points) {
                if (candidate.orderId() == point.orderId()) {
                    continue;
                }
                if (sameCluster(point, candidate)) {
                    neighborCount += 2;
                    continue;
                }
                if (sameRoad(point, candidate) || Math.hypot(point.x() - candidate.x(), point.y() - candidate.y()) <= 2.2d) {
                    neighborCount += 1;
                }
            }
            enriched.add(new DispatchRoutePoint(
                point.orderId(),
                point.addressLabel(),
                point.x(),
                point.y(),
                point.clusterName(),
                point.buildingName(),
                point.roadName(),
                point.locationTokens(),
                point.anchorDistance(),
                neighborCount
            ));
        }
        return enriched;
    }

    private String normalizeAddress(String addressLine) {
        if (addressLine == null || addressLine.isBlank()) {
            return "";
        }
        String normalized = addressLine
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('，', ' ')
            .replace('；', ' ')
            .replace('、', ' ')
            .replace('/', ' ')
            .replace('\\', ' ');
        normalized = CONTACT_PREFIX.matcher(normalized.strip()).replaceFirst("");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String extractClusterKey(String normalizedAddress, String areaCode) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = CLUSTER_PATTERN.matcher(normalizedAddress);
        while (matcher.find()) {
            String cleaned = trimGenericPrefix(matcher.group(1));
            if (cleaned.length() >= 2) {
                candidates.add(cleaned);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .findFirst()
                .orElse("");
        }
        if (areaCode != null && !areaCode.isBlank()) {
            return areaCode.trim();
        }
        return fallbackClusterKey(normalizedAddress);
    }

    private String extractRoadKey(String normalizedAddress) {
        Matcher matcher = ROAD_PATTERN.matcher(normalizedAddress);
        String best = "";
        while (matcher.find()) {
            String candidate = trimGenericPrefix(matcher.group(1));
            if (candidate.length() > best.length()) {
                best = candidate;
            }
        }
        return best;
    }

    private String extractBuildingKey(String normalizedAddress) {
        Matcher matcher = BUILDING_PATTERN.matcher(normalizedAddress);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private List<String> buildLocationTokens(
        String normalizedAddress,
        String clusterKey,
        String roadKey,
        String buildingKey
    ) {
        Set<String> tokens = new LinkedHashSet<>();
        addToken(tokens, clusterKey);
        addToken(tokens, roadKey);
        addToken(tokens, buildingKey);
        for (String fragment : normalizedAddress.split("[()\\[\\]\\- ]+")) {
            if (fragment.length() < 2 || fragment.length() > 12) {
                continue;
            }
            if (fragment.endsWith("省") || fragment.endsWith("市") || fragment.endsWith("区")) {
                continue;
            }
            addToken(tokens, fragment);
            if (tokens.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(tokens);
    }

    private void addToken(Set<String> tokens, String value) {
        String token = canonicalize(value);
        if (token.length() >= 2) {
            tokens.add(token);
        }
    }

    private String fallbackClusterKey(String normalizedAddress) {
        int buildingIndex = indexOfAny(normalizedAddress, "号楼", "栋", "幢", "座", "楼", "舍");
        if (buildingIndex > 2) {
            int start = Math.max(0, buildingIndex - 8);
            return canonicalize(normalizedAddress.substring(start, buildingIndex));
        }
        return canonicalize(normalizedAddress.length() <= 12 ? normalizedAddress : normalizedAddress.substring(0, 12));
    }

    private String trimGenericPrefix(String value) {
        String trimmed = value == null ? "" : value.trim();
        for (String prefix : GENERIC_PREFIXES) {
            trimmed = trimmed.replace(prefix, "");
        }
        return trimmed.trim();
    }

    private String canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
    }

    private Point estimatePoint(
        String normalizedAddress,
        String areaCode,
        String clusterKey,
        String roadKey,
        String buildingKey
    ) {
        String clusterSeed = firstNonBlank(clusterKey, areaCode, normalizedAddress);
        String angleSeed = firstNonBlank(roadKey, clusterSeed, normalizedAddress);
        String offsetSeed = firstNonBlank(buildingKey, normalizedAddress, clusterSeed);
        long clusterHash = stableHash(clusterSeed);
        long angleHash = stableHash(angleSeed);
        long offsetHash = stableHash(offsetSeed);
        long detailHash = stableHash(normalizedAddress);
        double baseRadius = 6.0d + positiveMod(clusterHash, 120) / 12.0d;
        double angle = 2.0d * Math.PI * positiveMod(angleHash, 360) / 360.0d;
        double x = Math.cos(angle) * baseRadius + (positiveMod(offsetHash, 21) - 10) / 6.0d;
        double y = Math.sin(angle) * baseRadius + (positiveMod(detailHash, 21) - 10) / 6.0d;
        return new Point(x, y);
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private long stableHash(String value) {
        long hash = 1125899906842597L;
        String normalized = value == null ? "" : value;
        for (int i = 0; i < normalized.length(); i++) {
            hash = 31L * hash + normalized.charAt(i);
        }
        return hash;
    }

    private int positiveMod(long value, int bound) {
        return (int) Math.floorMod(value, bound);
    }

    private int indexOfAny(String value, String... needles) {
        int best = -1;
        for (String needle : needles) {
            int index = value.indexOf(needle);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private boolean sameCluster(DispatchRoutePoint left, DispatchRoutePoint right) {
        return !left.clusterName().isBlank() && left.clusterName().equals(right.clusterName());
    }

    private boolean sameRoad(DispatchRoutePoint left, DispatchRoutePoint right) {
        return !left.roadName().isBlank() && left.roadName().equals(right.roadName());
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private record Point(double x, double y) {
    }

    public record RouteAddressSeed(long orderId, String addressLabel) {
    }
}
