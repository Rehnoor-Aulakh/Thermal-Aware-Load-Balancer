package com.rehnoor.websocketforloadbalancer.controller;

import com.rehnoor.websocketforloadbalancer.service.StressProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

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

    private final StressProxyService stressProxyService;

    public StressProxyController(StressProxyService stressProxyService) {
        this.stressProxyService = stressProxyService;
    }

    /**
     * Forwards a target-load request to a single backend server.
     * Called as: POST /proxy/stress/target-load?backendIp=100.68.255.12
     */
    @PostMapping("/stress/target-load")
    public ResponseEntity<?> proxyTargetLoad(
            @RequestParam String backendIp,
            @RequestBody String body
    ) {
        try {
            HttpResponse<String> response = stressProxyService.sendTargetLoad(backendIp, body);

            return ResponseEntity
                    .status(response.statusCode())
                    .body(response.body());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "backendIp", backendIp,
                    "message", "Failed to reach backend at " + stressProxyService.targetLoadUrl(backendIp)
                            + ": " + e.getMessage()
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

        List<Map<String, Object>> results = stressProxyService.sendTargetLoadToAll(backendIps, targetLoadValue);

        long successCount = results.stream().filter(r -> "success".equals(r.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "results", results,
                "totalServers", backendIps.size(),
                "successCount", successCount
        ));
    }
}
