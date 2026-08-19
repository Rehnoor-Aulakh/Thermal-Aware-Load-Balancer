package com.rehnoor.websocketforloadbalancer;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ServerHealthManager {

    // Map of backend IP to its current Thermal Score
    private final Map<String, Double> serverScores = new ConcurrentHashMap<>();

    /**
     * Updates the Thermal Score for a given backend server.
     * ThermalScore = 0.45 * PredictedTemp + 0.25 * CPUUsage + 0.15 * CPUPackagePower + 0.10 * GPUCoreTemperature + 0.05 * TargetLoad
     */
    public void updateScore(String backendIp, double predictedTemp, TelemetrySample sample) {
        if (!Double.isFinite(predictedTemp)) {
            return;
        }

        double score = 0.45 * predictedTemp
                + 0.25 * sample.cpuUsage()
                + 0.15 * sample.cpuPackagePower()
                + 0.10 * sample.gpuCoreTemperature()
                + 0.05 * sample.targetLoad();

        serverScores.put(backendIp, score);
    }

    /**
     * Removes a server from the queue (e.g., when it disconnects).
     */
    public void removeServer(String backendIp) {
        serverScores.remove(backendIp);
    }

    /**
     * Returns the IP of the server with the lowest Thermal Score.
     * If no servers are available, returns null.
     */
    public String getNextServer() {
        return serverScores.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Returns the current state of the priority queue, sorted from lowest score (best) to highest score (worst).
     */
    public List<Map<String, Object>> getQueueState() {
        return serverScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> Map.<String, Object>of(
                        "backendIp", entry.getKey(),
                        "thermalScore", entry.getValue()
                ))
                .collect(Collectors.toList());
    }
}
