package com.rehnoor.websocketforloadbalancer;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LoadBalancerWebSocketHandler extends TextWebSocketHandler {

    private static final int DEFAULT_BACKEND_PORT = 8086;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicReference<WebSocketSession> activeSession = new AtomicReference<>();
    private final AtomicReference<WebSocket> upstreamConnection = new AtomicReference<>();

    public LoadBalancerWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession existing = activeSession.getAndSet(session);
        if (existing != null && existing.isOpen() && !existing.getId().equals(session.getId())) {
            closeQuietly(existing);
        }

        String backendIp = getQueryParameter(session.getUri(), "backendIp");
        if (backendIp == null || backendIp.isBlank()) {
            sendJson(session, Map.of(
                    "type", "error",
                    "timestamp", Instant.now().toString(),
                    "message", "Missing backendIp query parameter"
            ));
            closeQuietly(session);
            return;
        }

        sendJson(session, Map.of(
                "type", "connectionEstablished",
                "timestamp", Instant.now().toString(),
                "backendIp", backendIp,
                "backendPort", DEFAULT_BACKEND_PORT
        ));

        connectUpstream(session, backendIp, DEFAULT_BACKEND_PORT);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (activeSession.compareAndSet(session, null)) {
            closeBackendConnection();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if (activeSession.compareAndSet(session, null)) {
            closeBackendConnection();
        }
        closeQuietly(session);
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException ignored) {
            closeQuietly(session);
        }
    }

    private void connectUpstream(WebSocketSession frontendSession, String backendIp, int backendPort) {
        closeBackendConnection();

        String backendUriText = "ws://" + backendIp + ":" + backendPort + "/telemetry";
        URI backendUri = URI.create(backendUriText);

        CompletableFuture<WebSocket> connectionFuture = httpClient.newWebSocketBuilder()
                .buildAsync(backendUri, new WebSocket.Listener() {
                    private final StringBuilder messageBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        if (last) {
                            forwardBackendMessage(messageBuffer.toString());
                            messageBuffer.setLength(0);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        sendJson(frontendSession, Map.of(
                                "type", "backendDisconnected",
                                "timestamp", Instant.now().toString(),
                                "message", reason == null || reason.isBlank() ? "Backend telemetry stream closed" : reason
                        ));
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        sendJson(frontendSession, Map.of(
                                "type", "error",
                                "timestamp", Instant.now().toString(),
                                "message", "Backend connection failed: " + error.getMessage()
                        ));
                    }
                });

        connectionFuture.whenComplete((webSocket, error) -> {
            if (error != null) {
                sendJson(frontendSession, Map.of(
                        "type", "error",
                        "timestamp", Instant.now().toString(),
                        "message", "Unable to connect to backend telemetry server at " + backendUriText
                ));
                return;
            }

            upstreamConnection.set(webSocket);
            sendJson(frontendSession, Map.of(
                    "type", "backendConnected",
                    "timestamp", Instant.now().toString(),
                    "message", "Connected to backend telemetry server",
                    "backendUri", backendUriText
            ));
        });
    }

    private void forwardBackendMessage(String message) {
        WebSocketSession session = activeSession.get();
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
        catch (IOException ignored) {
            closeQuietly(session);
        }
    }

    private void closeBackendConnection() {
        WebSocket upstream = upstreamConnection.getAndSet(null);
        if (upstream != null) {
            try {
                upstream.sendClose(WebSocket.NORMAL_CLOSURE, "frontend disconnected");
            }
            catch (Exception ignored) {
                upstream.abort();
            }
        }
    }

    private String getQueryParameter(URI uri, String key) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return null;
        }

        List<String> pairs = List.of(uri.getQuery().split("&"));
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 0) {
                continue;
            }

            String decodedKey = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (!key.equals(decodedKey)) {
                continue;
            }

            return parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
        }

        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
            // ignore close failures
        }
    }
}