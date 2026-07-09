package com.rehnoor.websocketforloadbalancer;

public record TelemetrySample(
        String timestamp,
        double cpuUsage,
        double cpuTemperature,
        double cpuPackagePower,
        double cpuAverageClock,
        double ramUsage,
        int networkConnections,
        int processCount
) {

    public double[] toModelFeatures() {
        return new double[]{
                cpuUsage,
                ramUsage,
                networkConnections,
                processCount,
                cpuPackagePower,
                cpuTemperature
        };
    }
}