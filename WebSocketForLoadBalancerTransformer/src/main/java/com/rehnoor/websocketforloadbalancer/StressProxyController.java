package com.rehnoor.websocketforloadbalancer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Proxies stress/target-load requests from the frontend to backend servers.
 *
 * The frontend browser cannot always reach the backend agents directly
 * (firewalls, NAT, Tailscale routing), so it sends the request to this
 * load-balancer which forwards it server-side.
 */
@RestController
@RequestMapping("/proxy")
@CrossOrigin(origins = "*")
public class StressProxyController {

    private static final int BACKEND_HTTP_PORT = 8080;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Forwards a target-load request to a single backend server.
     * Called as: POST /proxy/stress/target-load?backendIp=100.68.255.12
     */
    @PostMapping("/stress/target-load")
    public ResponseEntity<?> proxyTargetLoad(
            @RequestParam String backendIp,
            @RequestBody String body
    ) {
        String targetUrl = "http://" + backendIp.trim() + ":" + BACKEND_HTTP_PORT + "/stress/target-load";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return ResponseEntity
                    .status(response.statusCode())
                    .body(response.body());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "backendIp", backendIp,
                    "message", "Failed to reach backend at " + targetUrl + ": " + e.getMessage()
            ));
        }
    }

    /**
     * Forwards a target-load request to multiple backend servers in parallel.
     * Called as: POST /proxy/stress/target-load/batch
     * Body: { "backendIps": ["100.68.255.12", "100.94.91.14"], "targetLoad": 90 }
     */
    @PostMapping("/stress/target-load/batch")
    public ResponseEntity<?> proxyTargetLoadBatch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> backendIps = (List<String>) request.get("backendIps");
        Object targetLoadValue = request.get("targetLoad");

        if (backendIps == null || backendIps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "backendIps list is required"));
        }
        if (targetLoadValue == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "targetLoad is required"));
        }

        String payload = "{\"targetLoad\":" + targetLoadValue + "}";

        List<CompletableFuture<Map<String, Object>>> futures = backendIps.stream()
                .map(ip -> {
                    String targetUrl = "http://" + ip.trim() + ":" + BACKEND_HTTP_PORT + "/stress/target-load";
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                            .thenApply(resp -> Map.<String, Object>of(
                                    "backendIp", ip,
                                    "status", resp.statusCode() == 200 ? "success" : "error",
                                    "httpStatus", resp.statusCode()
                            ))
                            .exceptionally(ex -> Map.of(
                                    "backendIp", ip,
                                    "status", "error",
                                    "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
                            ));
                })
                .toList();

        List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long successCount = results.stream().filter(r -> "success".equals(r.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "results", results,
                "totalServers", backendIps.size(),
                "successCount", successCount
        ));
    }
}
