package com.rehnoor.backendserveragent.service;

/**
 * Turns a CPU temperature into a fan speed by drawing straight lines between a
 * few points, for example "55:30,70:45,80:65,85:80,90:100" (temperature:percent).
 * Below the first point the first speed is used; above the last, the last speed.
 */
final class FanCurve {

    private final double[] temperatures;
    private final double[] speeds;

    private FanCurve(double[] temperatures, double[] speeds) {
        this.temperatures = temperatures;
        this.speeds = speeds;
    }

    static FanCurve parse(String text) {
        String[] points = text.split(",");
        double[] temperatures = new double[points.length];
        double[] speeds = new double[points.length];

        for (int i = 0; i < points.length; i++) {
            String[] pair = points[i].trim().split(":");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Fan curve point must be temperature:percent, got " + points[i]);
            }
            temperatures[i] = Double.parseDouble(pair[0].trim());
            speeds[i] = Double.parseDouble(pair[1].trim());

            if (speeds[i] < 0 || speeds[i] > 100) {
                throw new IllegalArgumentException("Fan speed must be between 0 and 100: " + points[i]);
            }
            if (i > 0 && (temperatures[i] <= temperatures[i - 1] || speeds[i] < speeds[i - 1])) {
                throw new IllegalArgumentException(
                        "Fan curve temperatures must rise and speeds must not fall: " + text);
            }
        }
        return new FanCurve(temperatures, speeds);
    }

    int speedFor(double temperature) {
        if (temperature <= temperatures[0]) {
            return (int) Math.round(speeds[0]);
        }
        for (int i = 1; i < temperatures.length; i++) {
            if (temperature <= temperatures[i]) {
                double position = (temperature - temperatures[i - 1]) / (temperatures[i] - temperatures[i - 1]);
                return (int) Math.round(speeds[i - 1] + position * (speeds[i] - speeds[i - 1]));
            }
        }
        return (int) Math.round(speeds[speeds.length - 1]);
    }
}
