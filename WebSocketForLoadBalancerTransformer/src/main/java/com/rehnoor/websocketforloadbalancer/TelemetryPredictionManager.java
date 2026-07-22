package com.rehnoor.websocketforloadbalancer;


import ai.onnxruntime.OrtException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/// THIS CLASS WILL MAINTAIN ONE ROLLING SEQUENCE PER BACKEND SERVER
@Service
public class TelemetryPredictionManager {
    private static final int REQUIRED_SAMPLES = 39;
    private final TemperaturePredictionService predictionService;

    private final Map<String, Deque<TelemetrySample>> buffers = new ConcurrentHashMap<>();

    public TelemetryPredictionManager(TemperaturePredictionService predictionService){
        this.predictionService = predictionService;
    }

    public Float addSampleAndPredict(String backendId, TelemetrySample sample) throws OrtException {
        Deque<TelemetrySample> buffer = buffers.computeIfAbsent(backendId, key -> new ArrayDeque<>());

        double[][] rawSequence;

        synchronized (buffer){

            buffer.addLast(sample);

            if(buffer.size() > REQUIRED_SAMPLES){
                buffer.removeFirst();
            }
            if(buffer.size() < REQUIRED_SAMPLES){
                return null;
            }

            rawSequence= new double[REQUIRED_SAMPLES][7];

            int timestep = 0;

            // for each TelemetrySample in buffer
            for(TelemetrySample telemetry: buffer){
                rawSequence[timestep] = telemetry.toModelFeatures();
                timestep++;
            }
            return predictionService.predictFromRawTelemetry(rawSequence);
        }
    }

    public int getBufferSize(String backendId){
        Deque<TelemetrySample> buffer = buffers.get(backendId);
        if(buffer==null) return 0;
        synchronized (buffer){
            return buffer.size();
        }
    }

    public void removeBackend(String backendId){
        buffers.remove(backendId);
    }
}
