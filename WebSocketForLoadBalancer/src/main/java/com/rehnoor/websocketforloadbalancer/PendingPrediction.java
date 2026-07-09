package com.rehnoor.websocketforloadbalancer;

import java.time.LocalDateTime;

public record PendingPrediction(
        LocalDateTime predictionTime,
        LocalDateTime targetTime,
        double temperatureAtPrediction,
        double predictedDeltaT,
        double predictedTemperature
) {
}