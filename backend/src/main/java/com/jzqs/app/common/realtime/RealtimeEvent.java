package com.jzqs.app.common.realtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record RealtimeEvent(
    String eventType,
    Set<String> audiences,
    Map<String, Object> payload,
    String occurredAt
) {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RealtimeEvent {
        audiences = Set.copyOf(audiences == null ? Set.of() : audiences);
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        occurredAt = (occurredAt == null || occurredAt.isBlank())
            ? LocalDateTime.now().withNano(0).format(DATE_TIME_FORMATTER)
            : occurredAt;
    }

    public static Builder builder(String eventType) {
        return new Builder(eventType);
    }

    public static final class Builder {
        private final String eventType;
        private final Set<String> audiences = new LinkedHashSet<>();
        private final Map<String, Object> payload = new LinkedHashMap<>();

        private Builder(String eventType) {
            this.eventType = eventType == null ? "" : eventType.trim();
        }

        public Builder audience(String audience) {
            if (audience != null && !audience.isBlank()) {
                audiences.add(audience.trim());
            }
            return this;
        }

        public Builder audiences(Set<String> values) {
            if (values != null) {
                values.forEach(this::audience);
            }
            return this;
        }

        public Builder payload(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                payload.put(key, value);
            }
            return this;
        }

        public RealtimeEvent build() {
            return new RealtimeEvent(eventType, audiences, payload, null);
        }
    }
}
