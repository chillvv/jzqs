package com.jzqs.app.common.realtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public RealtimeWebSocketHandler(ObjectMapper objectMapper, RealtimeEventPublisher realtimeEventPublisher) {
        this.objectMapper = objectMapper;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
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
        } catch (BusinessException ex) {
            log.warn("realtime auth failed sessionId={} reason={}", session.getId(), ex.getMessage());
            sendErrorAndClose(session, ex.getMessage());
        } catch (Exception ex) {
            log.error("realtime message handling failed sessionId={}", session.getId(), ex);
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    private void sendErrorAndClose(WebSocketSession session, String reason) {
        try {
            session.sendMessage(new TextMessage(realtimeEventPublisher.errorMessage(reason)));
        } catch (IOException ignore) {
            // ignore
        }
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason(reason == null ? "auth failed" : reason));
            } catch (IOException ignore) {
                // ignore
            }
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
