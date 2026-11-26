package com.rehnoor.websocketforloadbalancer.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything that is written to a dashboard's WebSocket session. */
@Component
public class FrontendSessionMessenger {

    private final ObjectMapper objectMapper;

    public FrontendSessionMessenger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sends an event of the form {type, timestamp, ...details}. */
    public void sendEvent(WebSocketSession session, String type, Map<String, ?> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("timestamp", Instant.now().toString());
        payload.putAll(details);
        send(session, objectMapper.writeValueAsString(payload));
    }

    /** Sends text, such as a telemetry sample, without changing it. */
    public void send(WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException ignored) {
            closeQuietly(session);
        }
    }

    public void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
            // ignore close failures
        }
    }
}
