package com.rehnoor.backendserveragent.service;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * Publishes the most recent telemetry sample to load-balancer clients.
 *
 * The load balancer connects to {@code ws://<agent>:8086/telemetry}.  The
 * Java-WebSocket server accepts that path and sends the latest known sample as
 * soon as a client connects, so a dashboard does not have to wait for the next
 * polling interval. The load balancer sends its temperature predictions back
 * over the same connection.
 */
final class TelemetryWebSocketServer extends WebSocketServer {

    private final AtomicReference<String> lastTelemetry = new AtomicReference<>();

    private final Consumer<String> incomingMessages;

    TelemetryWebSocketServer(InetSocketAddress address, Consumer<String> incomingMessages) {
        super(address);
        this.incomingMessages = incomingMessages;
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        String snapshot = lastTelemetry.get();
        if (snapshot != null) {
            connection.send(snapshot);
        }
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        // No per-client resources to release.
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        incomingMessages.accept(message);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        if (connection == null) {
            System.err.println("Telemetry WebSocket error: " + exception.getMessage());
        }
    }

    @Override
    public void onStart() {
        System.out.println("Telemetry WebSocket listening on " + getAddress());
    }

    void publish(String json) {
        lastTelemetry.set(json);
        broadcast(json);
    }
}
