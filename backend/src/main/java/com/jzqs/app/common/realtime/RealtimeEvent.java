package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

public record RealtimeEvent(
    String eventType,
    Set<String> audiences,
    Payload payload,
    String occurredAt
) {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RealtimeEvent {
        audiences = Set.copyOf(audiences == null ? Set.of() : audiences);
        payload = payload == null ? Payload.empty() : payload;
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
        private final Payload.Builder payload = Payload.builder();

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
            return new RealtimeEvent(eventType, audiences, payload.build(), null);
        }
    }

    public static final class Payload {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private final ObjectNode values;

        private Payload(ObjectNode values) {
            this.values = values == null ? JsonNodeFactory.instance.objectNode() : values.deepCopy();
        }

        public static Payload empty() {
            return new Payload(JsonNodeFactory.instance.objectNode());
        }

        public static Builder builder() {
            return new Builder();
        }

        public Object get(String key) {
            if (key == null || key.isBlank()) {
                return null;
            }
            JsonNode node = values.get(key);
            return node == null || node.isNull() ? null : OBJECT_MAPPER.convertValue(node, Object.class);
        }

        @JsonValue
        public JsonNode toJson() {
            return values.deepCopy();
        }

        public static final class Builder {
            private final ObjectNode values = JsonNodeFactory.instance.objectNode();

            private Builder() {
            }

            public Builder put(String key, Object value) {
                if (key == null || key.isBlank() || value == null) {
                    return this;
                }
                values.set(key, OBJECT_MAPPER.valueToTree(value));
                return this;
            }

            public Payload build() {
                return new Payload(values);
            }
        }
    }
}
