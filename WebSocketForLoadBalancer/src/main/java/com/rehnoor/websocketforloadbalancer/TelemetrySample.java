package com.rehnoor.websocketforloadbalancer;

public record TelemetrySample(
        String timestamp,
        double cpuUsage,
        double cpuPackagePower,
        double gpuCoreTemperature,
        double gpuHotspotTemperature,
        double cpuEfficiencyAverageClock,
        double targetLoad,
        double cpuTemperature
) {

    public static final int MODEL_FEATURE_COUNT = 7;

    public double[] toModelFeatures() {
        return new double[]{
                cpuUsage,
                cpuPackagePower,
                gpuCoreTemperature,
                gpuHotspotTemperature,
                cpuEfficiencyAverageClock,
                targetLoad,
                cpuTemperature
        };
    }
}
