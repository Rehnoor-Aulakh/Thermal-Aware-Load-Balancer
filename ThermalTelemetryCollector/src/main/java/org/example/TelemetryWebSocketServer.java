package org.example;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class TelemetryWebSocketServer extends WebSocketServer {

    private final AtomicReference<String> lastTelemetry =
            new AtomicReference<>();

    public TelemetryWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        String snapshot =
                lastTelemetry.get();

        if(snapshot != null) {
            conn.send(snapshot);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onStart() {
    }

    public void publish(String json) {

        lastTelemetry.set(json);
        broadcast(json);
    }
}