package com.rehnoor.websocketforloadbalancer.model;

/**
 * What should be sent to the dashboard after one telemetry message.
 *
 * @param payload the telemetry JSON to forward, with the prediction and score
 *                added when they are available
 * @param warning a warning to send before the payload, or null
 */
public record ProcessedTelemetry(String payload, String warning) {

    public static ProcessedTelemetry of(String payload) {
        return new ProcessedTelemetry(payload, null);
    }
}
