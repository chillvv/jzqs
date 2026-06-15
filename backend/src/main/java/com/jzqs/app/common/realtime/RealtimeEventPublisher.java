package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.util.JwtUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
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
        String json = writeMessage(Map.of(
            "type", "EVENT",
            "eventType", event.eventType(),
            "occurredAt", event.occurredAt(),
            "payload", event.payload()
        ));
        sessionBindings.values().forEach(binding -> sendIfMatched(binding, event.audiences(), json));
    }

    public String authOkMessage(RealtimeViewer viewer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "AUTH_OK");
        body.put("viewer", Map.of(
            "userType", viewer.userType(),
            "userId", viewer.userId() == null ? "" : String.valueOf(viewer.userId()),
            "role", viewer.role(),
            "riderName", viewer.riderName()
        ));
        return writeMessage(body);
    }

    public String pongMessage() {
        return writeMessage(Map.of("type", "PONG"));
    }

    public RealtimeViewer resolveViewer(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少实时连接凭证");
        }
        Map<String, Object> claims = JwtUtils.parseToken(token.trim());
        if (isAdmin(claims)) {
            return RealtimeViewer.admin(numberValue(claims.get("userId")), stringValue(claims.get("role")));
        }
        if (claims.containsKey("riderId") || claims.containsKey("riderName")) {
            return RealtimeViewer.rider(numberValue(claims.get("riderId")), stringValue(claims.get("riderName")));
        }
        Long customerId = numberValue(claims.get("customerId"));
        if (customerId == null) {
            customerId = "customer".equalsIgnoreCase(stringValue(claims.get("userType")))
                ? numberValue(claims.get("userId"))
                : null;
        }
        if (customerId != null) {
            return RealtimeViewer.customer(customerId);
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "不支持的实时连接身份");
    }

    private boolean isAdmin(Map<String, Object> claims) {
        return "admin".equalsIgnoreCase(stringValue(claims.get("userType")))
            || !stringValue(claims.get("role")).isBlank();
    }

    private Long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private String writeMessage(Map<String, Object> body) {
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
}
