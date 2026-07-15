package com.rehnoor.backendserveragent.service;

import com.rehnoor.backendserveragent.model.SystemLog;
import com.rehnoor.backendserveragent.utils.JsonLogger;
import com.rehnoor.backendserveragent.utils.TelemetryCollector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Collects, persists, and streams one telemetry sample at a fixed interval. */
@Service
public class TelemetryService {

    private final StressService stressService;
    private final long intervalMs;
    private final int webSocketPort;
    private final String webSocketHost;

    private final TelemetryCollector collector = new TelemetryCollector();
    private final JsonLogger logger = new JsonLogger();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "telemetry-collector");
        thread.setDaemon(true);
        return thread;
    });

    private TelemetryWebSocketServer webSocketServer;

    public TelemetryService(
            StressService stressService,
            @Value("${telemetry.interval-ms:2000}") long intervalMs,
            @Value("${telemetry.websocket.port:8086}") int webSocketPort,
            @Value("${telemetry.websocket.host:0.0.0.0}") String webSocketHost
    ) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("telemetry.interval-ms must be positive");
        }
        this.stressService = stressService;
        this.intervalMs = intervalMs;
        this.webSocketPort = webSocketPort;
        this.webSocketHost = webSocketHost;
    }

    @PostConstruct
    public void start() {
        webSocketServer = new TelemetryWebSocketServer(new InetSocketAddress(webSocketHost, webSocketPort));
        webSocketServer.start();
        executor.scheduleWithFixedDelay(this::collectAndPublish, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
        if (webSocketServer != null) {
            try {
                webSocketServer.stop(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void collectAndPublish() {
        try {
            SystemLog log = collector.collect();
            log.targetLoad = stressService.getTargetLoad();
            String json = logger.write(log);
            webSocketServer.publish(json);
        } catch (Exception exception) {
            // Keep the scheduled task alive if one read or write fails.
            System.err.println("Telemetry collection failed: " + exception.getMessage());
        }
    }
}
