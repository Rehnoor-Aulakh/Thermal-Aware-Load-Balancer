package com.rehnoor.websocketforloadbalancer;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PredictionEvaluationService {
    private static final long FORECAST_SECONDS = 20;

    private final Map<String, Deque<PendingPrediction>> pendingPredictions = new ConcurrentHashMap<>();

    public void addPrediction(String backendId, TelemetrySample currentSample, double predictedDeltaT){
        LocalDateTime predictionTime = LocalDateTime.parse(currentSample.timestamp());
        LocalDateTime targetTime = predictionTime.plusSeconds(FORECAST_SECONDS);

        double predictedTemperature = currentSample.cpuTemperature() + predictedDeltaT;

        PendingPrediction  prediction  = new PendingPrediction(
                predictionTime,
                targetTime,
                currentSample.cpuTemperature(),
                predictedDeltaT,
                predictedTemperature
                );
        Deque<PendingPrediction> queue = pendingPredictions.computeIfAbsent(backendId, key -> new ArrayDeque<>());

        synchronized (queue){
            queue.addLast(prediction);
        }
    }

    public void evaluateReadyPredictions(String backendId, TelemetrySample currentSample){
        Deque<PendingPrediction> queue = pendingPredictions.get(backendId);
        if(queue==null) return;

        LocalDateTime actualTime = LocalDateTime.parse(currentSample.timestamp());

        synchronized (queue){
            while(!queue.isEmpty()){
                PendingPrediction prediction = queue.peekFirst();
                if(actualTime.isBefore(prediction.targetTime())){
                    break;
                }
                queue.removeFirst();

                double actualTemperature = currentSample.cpuTemperature();

                double actualDeltaT = actualTemperature - prediction.temperatureAtPrediction();

                double error = prediction.predictedTemperature() - actualTemperature;

                double absoluteError = Math.abs(error);

                double squaredError = error*error;

                PredictionEvaluation evaluation = new PredictionEvaluation(
                        prediction.predictionTime(),
                        prediction.targetTime(),
                        actualTime,
                        prediction.temperatureAtPrediction(),
                        prediction.predictedDeltaT(),
                        prediction.predictedTemperature(),
                        actualTemperature,
                        actualDeltaT,
                        error,
                        absoluteError,
                        squaredError
                );
                printEvaluation(backendId, evaluation);
            }

        }

    }
    private void printEvaluation(
            String backendId,
            PredictionEvaluation evaluation
    ) {

        long timingDifferenceMs =
                Duration.between(
                        evaluation.targetTime(),
                        evaluation.actualTime()
                ).toMillis();

        System.out.println(
                "\n========== PREDICTION EVALUATION =========="
        );

        System.out.println(
                "Backend: " + backendId
        );

        System.out.println(
                "Prediction Time: "
                        + evaluation.predictionTime()
        );

        System.out.println(
                "Target Time: "
                        + evaluation.targetTime()
        );

        System.out.println(
                "Actual Reading Time: "
                        + evaluation.actualTime()
        );

        System.out.println(
                "Target Timing Difference: "
                        + timingDifferenceMs
                        + " ms"
        );

        System.out.println(
                "Temperature at Prediction: "
                        + evaluation.temperatureAtPrediction()
                        + "°C"
        );

        System.out.println(
                "Predicted Delta T: "
                        + evaluation.predictedDeltaT()
                        + "°C"
        );

        System.out.println(
                "Actual Delta T: "
                        + evaluation.actualDeltaT()
                        + "°C"
        );

        System.out.println(
                "Predicted Temperature: "
                        + evaluation.predictedTemperature()
                        + "°C"
        );

        System.out.println(
                "Actual Temperature: "
                        + evaluation.actualTemperature()
                        + "°C"
        );

        System.out.println(
                "Error: "
                        + evaluation.error()
                        + "°C"
        );

        System.out.println(
                "Absolute Error: "
                        + evaluation.absoluteError()
                        + "°C"
        );

        System.out.println(
                "===========================================\n"
        );
    }


    public void removeBackend(String backendId) {
        pendingPredictions.remove(backendId);
    }

}
