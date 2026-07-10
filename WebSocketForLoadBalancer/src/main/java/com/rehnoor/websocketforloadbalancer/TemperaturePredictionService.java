package com.rehnoor.websocketforloadbalancer;


import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Service
public class TemperaturePredictionService {
    private static final String MODEL_PATH = "models/latest_cross_device_lstm.onnx";
    private static final String INPUT_NAME = "telemetry_input";

    private final OrtEnvironment environment;
    private final OrtSession session;

    private static final int TIMESTEPS = 20;
    private static final int FEATURE_COUNT = 6;

    private static final double[] FEATURE_MEANS = {
            48.72909645,   // cpuUsage
            68.83218936,   // ramUsage
            154.31479133,  // networkConnections
            276.66914296,  // processCount
            25.71630126,   // cpuPackagePower
            78.22816331    // cpuTemperature
    };

    private static final double[] FEATURE_SCALES = {
            35.33929007,   // cpuUsage
            3.34491899,    // ramUsage
            64.83917146,   // networkConnections
            8.64063309,    // processCount
            16.83180107,   // cpuPackagePower
            16.11174885    // cpuTemperature
    };

    public TemperaturePredictionService() throws IOException, OrtException {
        System.out.println("Loading CPU Temperature Prediction Model...");

        environment= OrtEnvironment.getEnvironment();
        ClassPathResource modelResource = new ClassPathResource(MODEL_PATH);

        // will be useful to load model like this when Spring Application is in JAR File
        byte[] modelBytes = Files.readAllBytes(modelResource.getFile().toPath());

        session = environment.createSession(modelBytes, new OrtSession.SessionOptions());

        System.out.println("ONNX Model Loaded Successfully!!!");

        System.out.println("Model inputs: " + session.getInputInfo().keySet());

        System.out.println("Model outputs: "+ session.getOutputInfo().keySet());


    }
//    The input must have this shape:
        //
        //float[1][20][6]
        //
        //1  = one server sequence being predicted
        //20 = last 20 telemetry readings
        //6  = features in each reading
//    modelInput
//│
//        └── [0]                         one batch
//    ├── [0]  → first reading
//    ├── [1]  → second reading
//    ├── ...
//            └── [19] → latest reading
    public float predict(float[][][] modelInput) throws OrtException{
        try(
             // OnnxTensor.createTensor(environment, modelInput) converts the normal Java multidimensional array into the tensor format ONNX Runtime understands.
                OnnxTensor inputTensor = OnnxTensor.createTensor(environment, modelInput);
                OrtSession.Result result = session.run(Map.of(INPUT_NAME,inputTensor))
             //  sends the tensor into the loaded model under the exact input name we verified
        ){
//            telemetry_input → LSTM → predicted_delta_t
            float[][] prediction = (float[][]) result.get(0).getValue();
            return prediction[0][0];
        }
    }

    @PreDestroy
    public void close() throws OrtException{
        session.close();
        System.out.println("ONNX prediction service closed.");
    }

    ///  Reproducing the Python StandardScalar
    private float[][][] scaleInput(double[][] rawSequence){
        if(rawSequence.length != TIMESTEPS){
            throw new IllegalArgumentException("Expected exactly 20 telemetry samples but received "+ rawSequence.length);
        }

        float[][][] modelInput = new float[1][TIMESTEPS][FEATURE_COUNT];

        for(int timestep = 0; timestep< TIMESTEPS; timestep++){
            if(rawSequence[timestep].length!=FEATURE_COUNT){
                throw new IllegalArgumentException("Each telemetry sample must have exactly 6 features");
            }
            for(int feature = 0; feature<FEATURE_COUNT; feature++){
                double scaledValue = (rawSequence[timestep][feature] - FEATURE_MEANS[feature]) / FEATURE_SCALES[feature];

                modelInput[0][timestep][feature] = (float) scaledValue;
            }
        }
        return modelInput;

    }

    ///  A public method that combines Scaling and Inference
    public float predictFromRawTelemetry(double[][] rawSequence) throws OrtException{
        float[][][] modelInput = scaleInput(rawSequence);

        return predict(modelInput);
    }

}
