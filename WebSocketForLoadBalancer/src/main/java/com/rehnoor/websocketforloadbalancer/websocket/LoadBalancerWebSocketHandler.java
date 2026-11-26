package com.rehnoor.websocketforloadbalancer.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Handles the dashboard's side of the WebSocket relay. */
@Component
public class LoadBalancerWebSocketHandler extends TextWebSocketHandler {

    private final BackendConnectionManager backendConnections;
    private final FrontendSessionMessenger messenger;

    public LoadBalancerWebSocketHandler(
            BackendConnectionManager backendConnections,
            FrontendSessionMessenger messenger
    ) {
        this.backendConnections = backendConnections;
        this.messenger = messenger;
    }

    // Frontend's session and it has backendIp of the request as a query parameter
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String backendIp = getQueryParameter(session.getUri(), "backendIp");
        if (backendIp == null || backendIp.isBlank()) {
            messenger.sendEvent(session, "error", Map.of(
                    "message", "Missing backendIp query parameter"
            ));
            messenger.closeQuietly(session);
            return;
        }

        // send json to the frontend via WebSocketConnection only that connection is now established
        // so most probably the IP will be of tailscale and on that IP's port 8086, we will make a websocket connection
        messenger.sendEvent(session, "connectionEstablished", Map.of(
                "backendIp", backendIp,
                "backendPort", BackendConnectionManager.BACKEND_PORT
        ));

        backendConnections.connect(session, backendIp);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        backendConnections.disconnect(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        backendConnections.disconnect(session.getId());
        messenger.closeQuietly(session);
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
}
