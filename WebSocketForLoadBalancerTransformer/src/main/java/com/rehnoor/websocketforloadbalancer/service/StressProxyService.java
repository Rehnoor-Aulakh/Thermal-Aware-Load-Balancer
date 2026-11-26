package com.rehnoor.websocketforloadbalancer.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Sends target-load requests to the backend agents. */
@Service
public class StressProxyService {

    private static final int BACKEND_HTTP_PORT = 8080;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String targetLoadUrl(String backendIp) {
        return "http://" + backendIp.trim() + ":" + BACKEND_HTTP_PORT + "/stress/target-load";
    }

    /** Forwards the body to one backend and returns that backend's response. */
    public HttpResponse<String> sendTargetLoad(String backendIp, String body)
            throws IOException, InterruptedException {
        return httpClient.send(buildRequest(backendIp, body), HttpResponse.BodyHandlers.ofString());
    }

    /** Sends the same target load to all backends in parallel; one result per backend. */
    public List<Map<String, Object>> sendTargetLoadToAll(List<String> backendIps, Object targetLoad) {
        String payload = "{\"targetLoad\":" + targetLoad + "}";

        List<CompletableFuture<Map<String, Object>>> futures = backendIps.stream()
                .map(ip -> httpClient.sendAsync(buildRequest(ip, payload), HttpResponse.BodyHandlers.ofString())
                        .thenApply(resp -> Map.<String, Object>of(
                                "backendIp", ip,
                                "status", resp.statusCode() == 200 ? "success" : "error",
                                "httpStatus", resp.statusCode()
                        ))
                        .exceptionally(ex -> Map.of(
                                "backendIp", ip,
                                "status", "error",
                                "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
                        )))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private HttpRequest buildRequest(String backendIp, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(targetLoadUrl(backendIp)))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
