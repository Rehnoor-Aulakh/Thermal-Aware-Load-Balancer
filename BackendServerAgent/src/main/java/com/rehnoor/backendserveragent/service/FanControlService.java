package com.rehnoor.backendserveragent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Chooses the fan speed from the measured CPU temperature and the load
 * balancer's latest prediction, and sends it to the ESP32.
 */
@Service
public class FanControlService {

    private static final String PREDICTION_TYPE = "prediction";

    private final FanSerialLink serialLink;
    private final FanSpeedPolicy policy;
    private final long predictionMaxAgeMs;
    private final LongSupplier clock;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Prediction latestPrediction;
    private int lastReportedSpeed = -1;

    private record Prediction(double temperature, long receivedAtMs) {
    }

    @Autowired
    public FanControlService(
            FanSerialLink serialLink,
            @Value("${fan.curve:55:30,70:45,80:65,85:80,90:100}") String curve,
            @Value("${fan.hysteresis-celsius:3}") double hysteresis,
            @Value("${fan.prediction.max-age-ms:6000}") long predictionMaxAgeMs
    ) {
        this(serialLink, curve, hysteresis, predictionMaxAgeMs, () -> System.nanoTime() / 1_000_000);
    }

    FanControlService(FanSerialLink serialLink, String curve, double hysteresis,
                      long predictionMaxAgeMs, LongSupplier clock) {
        this.serialLink = serialLink;
        this.policy = new FanSpeedPolicy(FanCurve.parse(curve), hysteresis);
        this.predictionMaxAgeMs = predictionMaxAgeMs;
        this.clock = clock;
    }

    /**
     * Handles a message from the load balancer, expected to look like
     * {@code {"type":"prediction","predictedTemperature":84.2}}. Anything else
     * is ignored.
     */
    public void handleMessage(String message) {
        try {
            JsonNode json = mapper.readTree(message);
            JsonNode value = json.path("predictedTemperature");

            if (!PREDICTION_TYPE.equals(json.path("type").asText()) || !value.isNumber()) {
                return;
            }
            double temperature = value.asDouble();
            if (temperature > 0 && temperature < 150) {
                latestPrediction = new Prediction(temperature, clock.getAsLong());
            }
        } catch (Exception exception) {
            System.err.println("Ignoring unreadable message: " + exception.getMessage());
        }
    }

    /**
     * Called once per telemetry sample.
     *
     * @return the fan speed in percent, or -1 when fan control is disabled
     */
    public synchronized int update(double currentTemperature) {
        if (!serialLink.isEnabled()) {
            return -1;
        }

        Double predicted = recentPrediction();
        int speed = policy.speedFor(currentTemperature, predicted);

        // Sent on every sample, even if unchanged: the ESP32 treats silence as a fault.
        serialLink.send(String.format(Locale.ROOT, "FAN %d %.1f %.1f",
                speed, currentTemperature, predicted == null ? -1.0 : predicted));

        if (speed != lastReportedSpeed) {
            System.out.println("Fan speed " + speed + "% (CPU " + currentTemperature + " C, predicted "
                    + (predicted == null ? "unavailable" : String.format(Locale.ROOT, "%.1f C", predicted)) + ")");
            lastReportedSpeed = speed;
        }
        return speed;
    }

    /** The latest prediction, unless it is too old to be trusted. */
    private Double recentPrediction() {
        Prediction prediction = latestPrediction;
        if (prediction == null || clock.getAsLong() - prediction.receivedAtMs() > predictionMaxAgeMs) {
            return null;
        }
        return prediction.temperature();
    }
}
