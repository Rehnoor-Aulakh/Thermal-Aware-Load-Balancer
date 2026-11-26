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

    /**
     * The agent reports -1 when a sensor could not be read. A sample without
     * CPU readings must not be scored: it would look like a very cool server.
     */
    public boolean hasValidCpuReadings() {
        return cpuTemperature > 0 && cpuTemperature < 110
                && cpuPackagePower >= 0
                && cpuUsage >= 0 && cpuUsage <= 100;
    }

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
