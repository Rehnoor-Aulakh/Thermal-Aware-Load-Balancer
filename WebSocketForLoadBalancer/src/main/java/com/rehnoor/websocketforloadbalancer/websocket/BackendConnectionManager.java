package com.rehnoor.websocketforloadbalancer.websocket;

import com.rehnoor.websocketforloadbalancer.model.ProcessedTelemetry;
import com.rehnoor.websocketforloadbalancer.service.TelemetryProcessingService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Owns the connections from the load balancer to the backend telemetry servers. */
@Component
public class BackendConnectionManager {

    // WebSocket Server port which will send the logs to this (client)
    static final int BACKEND_PORT = 8086;
    private static final long NO_DATA_WARNING_DELAY_SECONDS = 10;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // Both maps are keyed by the dashboard's session id
    private final Map<String, WebSocket> upstreamConnections = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> noDataWarnings = new ConcurrentHashMap<>();

    private final TelemetryProcessingService telemetryProcessing;
    private final FrontendSessionMessenger messenger;

    public BackendConnectionManager(
            TelemetryProcessingService telemetryProcessing,
            FrontendSessionMessenger messenger
    ) {
        this.telemetryProcessing = telemetryProcessing;
        this.messenger = messenger;
    }

    /** Opens the backend's telemetry stream and relays it to the dashboard session. */
    public void connect(WebSocketSession frontendSession, String backendIp) {
        closeBackendConnection(frontendSession.getId());

        String backendUriText = "ws://" + backendIp + ":" + BACKEND_PORT + "/telemetry";
        URI backendUri = URI.create(backendUriText);

        CompletableFuture<WebSocket> connectionFuture = httpClient.newWebSocketBuilder()
                .buildAsync(backendUri, new BackendListener(frontendSession, backendIp, backendUriText));

        connectionFuture.whenComplete((webSocket, error) -> {
            if (error != null) {
                cancelNoTelemetryWarning(frontendSession.getId());
                sendConnectionError(frontendSession, backendIp, backendUriText, error);
                return;
            }

            if (!frontendSession.isOpen()) {
                closeBackendConnection(webSocket);
                return;
            }

            WebSocket previous = upstreamConnections.put(frontendSession.getId(), webSocket);
            if (previous != null && previous != webSocket) {
                closeBackendConnection(previous);
            }
            messenger.sendEvent(frontendSession, "backendConnected", Map.of(
                    "message", "Connected to backend telemetry server",
                    "backendUri", backendUriText
            ));
        });
    }

    /** Called when the dashboard session goes away. */
    public void disconnect(String sessionId) {
        cancelNoTelemetryWarning(sessionId);
        closeBackendConnection(sessionId);
    }

    @PreDestroy
    public void closeAllBackendConnections() {
        upstreamConnections.values().forEach(this::closeBackendConnection);
        upstreamConnections.clear();
        noDataWarnings.values().forEach(task -> task.cancel(false));
        noDataWarnings.clear();
        scheduler.shutdownNow();
    }

    /** Receives messages and connection changes from one backend. */
    private class BackendListener implements WebSocket.Listener {

        private final WebSocketSession frontendSession;
        private final String backendIp;
        private final String backendUriText;
        private final StringBuilder messageBuffer = new StringBuilder();

        BackendListener(WebSocketSession frontendSession, String backendIp, String backendUriText) {
            this.frontendSession = frontendSession;
            this.backendIp = backendIp;
            this.backendUriText = backendUriText;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            scheduleNoTelemetryWarning(frontendSession, backendIp);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String telemetryJson = messageBuffer.toString();
                // Data arrived, so restart the silence countdown.
                if (frontendSession.isOpen()) {
                    scheduleNoTelemetryWarning(frontendSession, backendIp);
                }
                relay(webSocket, telemetryJson);

                messageBuffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            cancelNoTelemetryWarning(frontendSession.getId());
            upstreamConnections.remove(frontendSession.getId(), webSocket);
            messenger.sendEvent(frontendSession, "backendDisconnected", Map.of(
                    "message", reason == null || reason.isBlank() ? "Backend telemetry stream closed" : reason
            ));
            telemetryProcessing.discardBackendState(backendIp);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            cancelNoTelemetryWarning(frontendSession.getId());
            upstreamConnections.remove(frontendSession.getId(), webSocket);
            telemetryProcessing.discardLiveState(backendIp);
            sendConnectionError(frontendSession, backendIp, backendUriText, error);
        }

        private void relay(WebSocket webSocket, String telemetryJson) {
            ProcessedTelemetry processed = telemetryProcessing.process(backendIp, telemetryJson);

            if (processed.predictedTemperature() != null) {
                sendPrediction(webSocket, processed.predictedTemperature());
            }

            if (processed.warning() != null) {
                messenger.sendEvent(frontendSession, "warning", Map.of("message", processed.warning()));
            }
            messenger.send(frontendSession, processed.payload());
        }
    }

    /** Tells the backend its forecast temperature, which it uses for fan control. */
    private void sendPrediction(WebSocket webSocket, double predictedTemperature) {
        String message = "{\"type\":\"prediction\",\"predictedTemperature\":" + predictedTemperature + "}";

        webSocket.sendText(message, true).exceptionally(error -> {
            // The next sample brings a newer prediction, so there is no retry.
            System.err.println("Could not send a prediction to a backend: " + error.getMessage());
            return null;
        });
    }

    private void closeBackendConnection(String sessionId) {
        WebSocket upstream = upstreamConnections.remove(sessionId);
        closeBackendConnection(upstream);
    }

    private void closeBackendConnection(WebSocket upstream) {
        if (upstream != null) {
            try {
                upstream.sendClose(WebSocket.NORMAL_CLOSURE, "frontend disconnected");
            }
            catch (Exception ignored) {
                upstream.abort();
            }
        }
    }

    private void sendConnectionError(WebSocketSession session, String backendIp, String backendUriText, Throwable error) {
        String cause = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "the backend server did not respond"
                : error.getMessage();

        messenger.sendEvent(session, "error", Map.of(
                "message", "Unable to connect to backend IP " + backendIp + " at " + backendUriText + ". "
                        + "Please verify the backend is reachable and that the telemetry connector and Libre Hardware Monitor are running. "
                        + "Details: " + cause
        ));
    }

    private void scheduleNoTelemetryWarning(WebSocketSession frontendSession, String backendIp) {
        cancelNoTelemetryWarning(frontendSession.getId());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (frontendSession.isOpen()) {
                // A silent backend must not keep its old place in the queue.
                telemetryProcessing.discardBackendState(backendIp);
                messenger.sendEvent(frontendSession, "warning", Map.of(
                        "message", "No telemetry data received from " + backendIp + ". Please make sure your telemetry connector and Libre Hardware Monitor are running."
                ));
            }
            noDataWarnings.remove(frontendSession.getId());
        }, NO_DATA_WARNING_DELAY_SECONDS, TimeUnit.SECONDS);
        noDataWarnings.put(frontendSession.getId(), future);
    }

    private void cancelNoTelemetryWarning(String sessionId) {
        ScheduledFuture<?> future = noDataWarnings.remove(sessionId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
