package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);
    private final ObjectMapper objectMapper;
    private final Map<String, SessionBinding> sessionBindings = new ConcurrentHashMap<>();

    public RealtimeEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RealtimeViewer bindSession(WebSocketSession session, String token) {
        RealtimeViewer viewer = resolveViewer(token);
        sessionBindings.put(session.getId(), new SessionBinding(session, viewer));
        return viewer;
    }

    public void unbindSession(WebSocketSession session) {
        if (session != null) {
            sessionBindings.remove(session.getId());
        }
    }

    public void publish(RealtimeEvent event) {
        if (event == null || event.audiences().isEmpty()) {
            return;
        }
        String json = writeMessage(new RealtimeEventMessage(
            "EVENT",
            event.eventType(),
            event.occurredAt(),
            event.payload().toJson()
        ));
        sessionBindings.values().forEach(binding -> sendIfMatched(binding, event.audiences(), json));
    }

    public String authOkMessage(RealtimeViewer viewer) {
        return writeMessage(new RealtimeAuthOkMessage(
            "AUTH_OK",
            new RealtimeViewerPayload(
                viewer.userType(),
                viewer.userId() == null ? "" : String.valueOf(viewer.userId()),
                viewer.role(),
                viewer.riderName()
            )
        ));
    }

    public String pongMessage() {
        return writeMessage(new RealtimePongMessage("PONG"));
    }

    public RealtimeViewer resolveViewer(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少实时连接凭证");
        }
        JwtClaims claims = JwtUtils.parseToken(token.trim());
        if (isAdmin(claims)) {
            return RealtimeViewer.admin(claims.userId(), stringValue(claims.role()));
        }
        if (claims.riderId() != null || claims.riderName() != null) {
            return RealtimeViewer.rider(claims.riderId(), stringValue(claims.riderName()));
        }
        Long customerId = claims.customerId();
        if (customerId == null) {
            customerId = "customer".equalsIgnoreCase(stringValue(claims.userType()))
                ? claims.userId()
                : null;
        }
        if (customerId != null) {
            return RealtimeViewer.customer(customerId);
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "不支持的实时连接身份");
    }

    private boolean isAdmin(JwtClaims claims) {
        return claims.isAdmin();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void sendIfMatched(SessionBinding binding, Set<String> audiences, String json) {
        if (!binding.viewer().matches(audiences)) {
            return;
        }
        WebSocketSession session = binding.session();
        if (session == null || !session.isOpen()) {
            sessionBindings.remove(binding.sessionId());
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException ex) {
            sessionBindings.remove(binding.sessionId());
            log.warn(
                "realtime publish delivery failed eventType={} sessionId={} userType={} userId={} riderName={} audiences={} reason={}",
                readMessageField(json, "eventType"),
                binding.sessionId(),
                binding.viewer().userType(),
                binding.viewer().userId(),
                binding.viewer().riderName(),
                audiences,
                ex.getMessage()
            );
        }
    }

    private String readMessageField(String json, String fieldName) {
        try {
            return objectMapper.readTree(json).path(fieldName).asText("");
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private String writeMessage(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("realtime message serialize failed", ex);
        }
    }

    private record SessionBinding(String sessionId, WebSocketSession session, RealtimeViewer viewer) {
        private SessionBinding(WebSocketSession session, RealtimeViewer viewer) {
            this(session.getId(), session, viewer);
        }
    }

    private record RealtimeEventMessage(
        String type,
        String eventType,
        String occurredAt,
        JsonNode payload
    ) {
    }

    private record RealtimePongMessage(String type) {
    }

    private record RealtimeAuthOkMessage(
        String type,
        RealtimeViewerPayload viewer
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record RealtimeViewerPayload(
        String userType,
        String userId,
        String role,
        String riderName
    ) {
    }
}
