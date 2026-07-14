package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public RealtimeWebSocketHandler(ObjectMapper objectMapper, RealtimeEventPublisher realtimeEventPublisher) {
        this.objectMapper = objectMapper;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RealtimeInboundMessage body = objectMapper.readValue(message.getPayload(), RealtimeInboundMessage.class);
        String type = body.normalizedType();
        if ("AUTH".equals(type)) {
            RealtimeViewer viewer = realtimeEventPublisher.bindSession(session, body.normalizedToken());
            session.sendMessage(new TextMessage(realtimeEventPublisher.authOkMessage(viewer)));
            return;
        }
        if ("PING".equals(type)) {
            session.sendMessage(new TextMessage(realtimeEventPublisher.pongMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeEventPublisher.unbindSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        realtimeEventPublisher.unbindSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RealtimeInboundMessage(String type, String token) {
        private String normalizedType() {
            return normalize(type).toUpperCase();
        }

        private String normalizedToken() {
            return normalize(token);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
