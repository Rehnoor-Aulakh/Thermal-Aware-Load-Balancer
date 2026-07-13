package com.rehnoor.websocketforloadbalancer;

public record TelemetrySample(
        String timestamp,
        double cpuUsage,
        double cpuTemperature,
        double cpuPackagePower,
        double gpuCoreTemperature,
        double gpuHotspotTemperature,
        double cpuEfficiencyAverageClock
) {

    public double[] toModelFeatures() {
        return new double[]{
                cpuUsage,
                cpuPackagePower,
                gpuCoreTemperature,
                gpuHotspotTemperature,
                cpuEfficiencyAverageClock,
                cpuTemperature
        };
    }
}