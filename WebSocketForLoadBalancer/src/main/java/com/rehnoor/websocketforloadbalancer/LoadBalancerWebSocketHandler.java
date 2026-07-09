package com.rehnoor.websocketforloadbalancer;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;

@Component
public class LoadBalancerWebSocketHandler extends TextWebSocketHandler {

    // WebSocket Server port which will send the logs to this (client)
    private static final int DEFAULT_BACKEND_PORT = 8086;
    private static final long NO_DATA_WARNING_DELAY_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, WebSocket> upstreamConnections = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> noDataWarnings = new ConcurrentHashMap<>();
<<<<<<< HEAD
=======
    private final TelemetryPredictionManager predictionManager;
    private PredictionEvaluationService evaluationService = null;
>>>>>>> 33696ffe8c3de42c97d58c76c9f2f5a9aaf6eead

    public LoadBalancerWebSocketHandler(ObjectMapper objectMapper, TelemetryPredictionManager predictionManager, PredictionEvaluationService evaluationService) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.predictionManager = predictionManager;
        this.evaluationService = evaluationService;
    }

    // Frontend's session and it has backendIp of the request as a query parameter
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
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

        // send json to the frontend via WebSocketConnection only that connection is now established
        // so most probably the IP will be of tailscale and on that IP's port 8086, we will make a websocket connection using connectUpstream function
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
        cancelNoTelemetryWarning(session.getId());
        closeBackendConnection(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cancelNoTelemetryWarning(session.getId());
        closeBackendConnection(session.getId());
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
        closeBackendConnection(frontendSession.getId());

        String backendUriText = "ws://" + backendIp + ":" + backendPort + "/telemetry";
        URI backendUri = URI.create(backendUriText);

        CompletableFuture<WebSocket> connectionFuture = httpClient.newWebSocketBuilder()
                .buildAsync(backendUri, new WebSocket.Listener() {
                    private final StringBuilder messageBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        scheduleNoTelemetryWarning(frontendSession, backendIp);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        if (last) {
<<<<<<< HEAD
                            resetNoTelemetryWarning(frontendSession, backendIp);
                            forwardBackendMessage(frontendSession, messageBuffer.toString());
=======
                            String telemetryJson = messageBuffer.toString();
                            processTelemetryMessage(frontendSession, backendIp, telemetryJson);

>>>>>>> 33696ffe8c3de42c97d58c76c9f2f5a9aaf6eead
                            messageBuffer.setLength(0);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        cancelNoTelemetryWarning(frontendSession.getId());
                        upstreamConnections.remove(frontendSession.getId(), webSocket);
                        sendJson(frontendSession, Map.of(
                                "type", "backendDisconnected",
                                "timestamp", Instant.now().toString(),
                                "message", reason == null || reason.isBlank() ? "Backend telemetry stream closed" : reason
                        ));
                        predictionManager.removeBackend(backendIp);
                        evaluationService.removeBackend(backendIp);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        cancelNoTelemetryWarning(frontendSession.getId());
                        upstreamConnections.remove(frontendSession.getId(), webSocket);
                        predictionManager.removeBackend(backendIp);
                        sendJson(frontendSession, Map.of(
                                "type", "error",
                                "timestamp", Instant.now().toString(),
                                "message", buildConnectionErrorMessage(backendIp, backendUriText, error)
                        ));
                    }
                });

        connectionFuture.whenComplete((webSocket, error) -> {
            if (error != null) {
                cancelNoTelemetryWarning(frontendSession.getId());
                sendJson(frontendSession, Map.of(
                        "type", "error",
                        "timestamp", Instant.now().toString(),
                        "message", buildConnectionErrorMessage(backendIp, backendUriText, error)
                ));
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
            sendJson(frontendSession, Map.of(
                    "type", "backendConnected",
                    "timestamp", Instant.now().toString(),
                    "message", "Connected to backend telemetry server",
                    "backendUri", backendUriText
            ));
        });
    }

    private void forwardBackendMessage(WebSocketSession session, String message) {
        if (!session.isOpen()) {
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

    @PreDestroy
    public void closeAllBackendConnections() {
        upstreamConnections.values().forEach(this::closeBackendConnection);
        upstreamConnections.clear();
        noDataWarnings.values().forEach(task -> task.cancel(false));
        noDataWarnings.clear();
        scheduler.shutdownNow();
    }

    String buildConnectionErrorMessage(String backendIp, String backendUriText, Throwable error) {
        String cause = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "the backend server did not respond"
                : error.getMessage();

        return "Unable to connect to backend IP " + backendIp + " at " + backendUriText + ". "
                + "Please verify the backend is reachable and that the telemetry connector and Libre Hardware Monitor are running. "
                + "Details: " + cause;
    }

    private void scheduleNoTelemetryWarning(WebSocketSession frontendSession, String backendIp) {
        cancelNoTelemetryWarning(frontendSession.getId());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (frontendSession.isOpen()) {
                sendJson(frontendSession, Map.of(
                        "type", "warning",
                        "timestamp", Instant.now().toString(),
                        "message", "No telemetry data received from " + backendIp + ". Please make sure your telemetry connector and Libre Hardware Monitor are running."
                ));
            }
            noDataWarnings.remove(frontendSession.getId());
        }, NO_DATA_WARNING_DELAY_SECONDS, TimeUnit.SECONDS);
        noDataWarnings.put(frontendSession.getId(), future);
    }

    private void resetNoTelemetryWarning(WebSocketSession frontendSession, String backendIp) {
        scheduleNoTelemetryWarning(frontendSession, backendIp);
    }

    private void cancelNoTelemetryWarning(String sessionId) {
        ScheduledFuture<?> future = noDataWarnings.remove(sessionId);
        if (future != null) {
            future.cancel(false);
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

    private void processTelemetryMessage(
            WebSocketSession frontendSession,
            String backendId,
            String telemetryJson
    ) {

        try {

            // Convert incoming JSON into Java object
            TelemetrySample sample =
                    objectMapper.readValue(
                            telemetryJson,
                            TelemetrySample.class
                    );

            /*
             * First, use this new real telemetry reading
             * to evaluate predictions made about 20 seconds ago.
             */
            evaluationService.evaluateReadyPredictions(
                    backendId,
                    sample
            );

            /*
             * Add the current sample to the rolling LSTM buffer.
             *
             * Returns null until 20 samples are available.
             */
            Float predictedDeltaT =
                    predictionManager.addSampleAndPredict(
                            backendId,
                            sample
                    );

            /*
             * The model is still warming up.
             *
             * IMPORTANT:
             * Do not call addPrediction() here because
             * predictedDeltaT is null.
             */
            if (predictedDeltaT == null) {

                System.out.println(
                        "Backend "
                                + backendId
                                + " | Buffer: "
                                + predictionManager
                                .getBufferSize(backendId)
                                + "/20"
                );

                forwardBackendMessage(
                        frontendSession,
                        telemetryJson
                );

                return;
            }

            /*
             * Only store a future prediction when the model
             * actually produced one.
             */
            evaluationService.addPrediction(
                    backendId,
                    sample,
                    predictedDeltaT
            );

            double predictedTemperature =
                    sample.cpuTemperature()
                            + predictedDeltaT;

            System.out.println(
                    "Backend "
                            + backendId
                            + " | Current Temp: "
                            + sample.cpuTemperature()
                            + "°C"
                            + " | Predicted Delta T: "
                            + predictedDeltaT
                            + "°C"
                            + " | Predicted Temp: "
                            + predictedTemperature
                            + "°C"
            );

            forwardBackendMessage(
                    frontendSession,
                    telemetryJson
            );

        } catch (Exception exception) {

            System.err.println(
                    "Failed to process telemetry from "
                            + backendId
                            + ": "
                            + exception.getMessage()
            );

            /*
             * Prediction problems should never stop the
             * original telemetry stream.
             */
            forwardBackendMessage(
                    frontendSession,
                    telemetryJson
            );
        }
    }}
