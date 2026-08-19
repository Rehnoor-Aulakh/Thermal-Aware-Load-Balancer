package com.rehnoor.websocketforloadbalancer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/lb")
@CrossOrigin(origins = "*")
public class LoadBalancerController {

    private final ServerHealthManager serverHealthManager;

    public LoadBalancerController(ServerHealthManager serverHealthManager) {
        this.serverHealthManager = serverHealthManager;
    }

    /**
     * Returns the state of the priority queue.
     */
    @GetMapping("/queue")
    public ResponseEntity<?> getQueueState() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "queue", serverHealthManager.getQueueState()
        ));
    }

    /**
     * Returns the next best server according to the priority queue.
     */
    @GetMapping("/next-server")
    public ResponseEntity<?> getNextServer() {
        String nextServer = serverHealthManager.getNextServer();
        if (nextServer == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "error",
                    "message", "No servers available in the priority queue"
                ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "backendIp", nextServer
        ));
    }
}
