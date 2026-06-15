package com.jzqs.app.common.realtime;

import java.util.LinkedHashSet;
import java.util.Set;

public record RealtimeViewer(
    String userType,
    Long userId,
    String role,
    String riderName,
    Set<String> audiences
) {
    public RealtimeViewer {
        audiences = Set.copyOf(audiences == null ? Set.of() : audiences);
    }

    public static RealtimeViewer admin(Long userId, String role) {
        return new RealtimeViewer("admin", userId, normalize(role), "", Set.of("admin"));
    }

    public static RealtimeViewer rider(Long riderId, String riderName) {
        Set<String> audiences = new LinkedHashSet<>();
        audiences.add("rider:all");
        if (riderId != null) {
            audiences.add("rider:id:" + riderId);
        }
        String normalizedName = normalize(riderName);
        if (!normalizedName.isBlank()) {
            audiences.add("rider:name:" + normalizedName);
        }
        return new RealtimeViewer("rider", riderId, "", normalizedName, audiences);
    }

    public static RealtimeViewer customer(Long customerId) {
        Set<String> audiences = new LinkedHashSet<>();
        audiences.add("customer:all");
        if (customerId != null) {
            audiences.add("customer:id:" + customerId);
        }
        return new RealtimeViewer("customer", customerId, "", "", audiences);
    }

    public boolean matches(Set<String> eventAudiences) {
        if (eventAudiences == null || eventAudiences.isEmpty()) {
            return false;
        }
        for (String audience : eventAudiences) {
            if (audiences.contains(audience)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
