package com.rehnoor.websocketforloadbalancer.model;

/**
 * What should be sent onwards after one telemetry message.
 *
 * @param payload the telemetry JSON to forward, with the prediction and score
 *                added when they are available
 * @param warning a warning to send before the payload, or null
 * @param predictedTemperature the new forecast to send back to the backend,
 *                             or null if there is none
 */
public record ProcessedTelemetry(String payload, String warning, Double predictedTemperature) {

    public static ProcessedTelemetry of(String payload) {
        return new ProcessedTelemetry(payload, null, null);
    }
}
