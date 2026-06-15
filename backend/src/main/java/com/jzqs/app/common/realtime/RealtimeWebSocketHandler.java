package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public RealtimeWebSocketHandler(ObjectMapper objectMapper, RealtimeEventPublisher realtimeEventPublisher) {
        this.objectMapper = objectMapper;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> body = objectMapper.readValue(message.getPayload(), MAP_TYPE);
        String type = String.valueOf(body.getOrDefault("type", "")).trim().toUpperCase();
        if ("AUTH".equals(type)) {
            String token = String.valueOf(body.getOrDefault("token", "")).trim();
            RealtimeViewer viewer = realtimeEventPublisher.bindSession(session, token);
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
}
