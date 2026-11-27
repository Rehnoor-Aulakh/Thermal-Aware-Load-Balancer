package com.rehnoor.websocketforloadbalancer.service;

import com.rehnoor.websocketforloadbalancer.model.ProcessedTelemetry;
import com.rehnoor.websocketforloadbalancer.model.TelemetrySample;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Turns one raw telemetry message into a prediction and a Thermal Score. */
@Service
public class TelemetryProcessingService {

    private final ObjectMapper objectMapper;
    private final TelemetryPredictionManager predictionManager;
    private final PredictionEvaluationService evaluationService;
    private final ServerHealthManager serverHealthManager;
    private final Set<String> backendsWithSensorFault = ConcurrentHashMap.newKeySet();

    public TelemetryProcessingService(
            ObjectMapper objectMapper,
            TelemetryPredictionManager predictionManager,
            PredictionEvaluationService evaluationService,
            ServerHealthManager serverHealthManager
    ) {
        this.objectMapper = objectMapper;
        this.predictionManager = predictionManager;
        this.evaluationService = evaluationService;
        this.serverHealthManager = serverHealthManager;
    }

    public ProcessedTelemetry process(String backendId, String telemetryJson) {

        try {

            // Convert incoming JSON into Java object
            TelemetrySample sample =
                    objectMapper.readValue(
                            telemetryJson,
                            TelemetrySample.class
                    );

            if (!sample.hasValidCpuReadings()) {
                discardBackendState(backendId);

                // Report once per fault, not on every sample.
                String warning = backendsWithSensorFault.add(backendId)
                        ? "CPU sensor readings from " + backendId
                        + " are missing or invalid, so it is out of the priority queue. "
                        + "Check that Libre Hardware Monitor is running."
                        : null;

                return new ProcessedTelemetry(telemetryJson, warning, null);
            }
            backendsWithSensorFault.remove(backendId);

            /*
             * First, use this new real telemetry reading
             * to evaluate predictions whose target time has been reached.
             */
            evaluationService.evaluateReadyPredictions(
                    backendId,
                    sample
            );

            /*
             * Add the current sample to the rolling model buffer.
             *
             * Returns null until the buffer is full.
             */
            Float predictedDeltaT =
                    predictionManager.addSampleAndPredict(
                            backendId,
                            sample
                    );

            /*
             * The model is still warming up.
             *
             * IMPORTANT:
             * Do not call addPrediction() here because
             * predictedDeltaT is null.
             */
            if (predictedDeltaT == null) {

                System.out.println(
                        "Backend "
                                + backendId
                                + " | Buffer: "
                                + predictionManager
                                .getBufferSize(backendId)
                                + "/20"
                );

                return ProcessedTelemetry.of(telemetryJson);
            }

            /*
             * Only store a future prediction when the model
             * actually produced one.
             */
            evaluationService.addPrediction(
                    backendId,
                    sample,
                    predictedDeltaT
            );

            double predictedTemperature =
                    sample.cpuTemperature()
                            + predictedDeltaT;

            serverHealthManager.updateScore(backendId, predictedTemperature, sample);

            double thermalScore = serverHealthManager.calculateScore(predictedTemperature, sample);

            System.out.println(
                    "Backend "
                            + backendId
                            + " | Current Temp: "
                            + sample.cpuTemperature()
                            + "°C"
                            + " | Predicted Delta T: "
                            + predictedDeltaT
                            + "°C"
                            + " | Predicted Temp: "
                            + predictedTemperature
                            + "°C"
                            + " | Thermal Score: "
                            + String.format("%.2f", thermalScore)
            );

            return new ProcessedTelemetry(
                    withPredictionAndScore(telemetryJson, predictedTemperature, thermalScore),
                    null,
                    Double.isFinite(predictedTemperature) ? predictedTemperature : null
            );

        } catch (Exception exception) {

            System.err.println(
                    "Failed to process telemetry from "
                            + backendId
                            + ": "
                            + exception.getMessage()
            );

            /*
             * Prediction problems should never stop the
             * original telemetry stream.
             */
            return ProcessedTelemetry.of(telemetryJson);
        }
    }

    /** Drops buffered samples, pending predictions and the queue entry. */
    public void discardBackendState(String backendId) {
        predictionManager.removeBackend(backendId);
        evaluationService.removeBackend(backendId);
        serverHealthManager.removeServer(backendId);
    }

    /**
     * Used when the connection fails with an error. Pending predictions are
     * kept; only the sample buffer and the queue entry are removed.
     */
    public void discardLiveState(String backendId) {
        predictionManager.removeBackend(backendId);
        serverHealthManager.removeServer(backendId);
    }

    private String withPredictionAndScore(String telemetryJson, double predictedTemperature, double thermalScore) {
        if (!Double.isFinite(predictedTemperature)) {
            return telemetryJson;
        }

        int objectEnd = telemetryJson.lastIndexOf('}');
        if (objectEnd < 0) {
            return telemetryJson;
        }

        return telemetryJson.substring(0, objectEnd)
                + ",\"predictedTemperature\":" + predictedTemperature
                + ",\"thermalScore\":" + thermalScore
                + telemetryJson.substring(objectEnd);
    }
}
