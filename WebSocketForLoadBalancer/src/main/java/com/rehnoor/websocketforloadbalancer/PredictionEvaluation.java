package com.rehnoor.websocketforloadbalancer;

import java.time.LocalDateTime;

public record PredictionEvaluation(
        LocalDateTime predictionTime,
        LocalDateTime targetTime,
        LocalDateTime actualTime,

        double temperatureAtPrediction,
        double predictedDeltaT,
        double predictedTemperature,
        double actualTemperature,

        double actualDeltaT,
        double error,
        double absoluteError,
        double squaredError
) {
}
