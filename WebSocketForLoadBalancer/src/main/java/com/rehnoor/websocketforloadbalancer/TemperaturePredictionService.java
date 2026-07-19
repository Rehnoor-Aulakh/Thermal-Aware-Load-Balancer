package com.rehnoor.websocketforloadbalancer;


import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import jakarta.annotation.PreDestroy;

@Service
public class TemperaturePredictionService {
    private static final String MODEL_PATH = "models/4_datasets_best_cross_device_gru.onnx";
    private static final String INPUT_NAME = "telemetry_input";

    private final OrtEnvironment environment;
    private final OrtSession session;

    private static final int TIMESTEPS = 20;
    private static final int FEATURE_COUNT = TelemetrySample.MODEL_FEATURE_COUNT;

    private static final double[] FEATURE_MEANS = {

            49.26257083,

            28.41215306,

            52.65582340,

            59.62744522,

            1833.21964534,

            43.79285744,

            82.02674002

    };

    private static final double[] FEATURE_SCALES = {

            34.28571687,

            15.76849644,

            7.78824243,

            8.26603192,

            1040.52568746,

            35.88821269,

            14.76576228

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

        validateModelFeatureCount();


    }

    private void validateModelFeatureCount() throws OrtException {
        var inputInfo = session.getInputInfo().get(INPUT_NAME);
        if (inputInfo == null || !(inputInfo.getInfo() instanceof TensorInfo tensorInfo)) {
            throw new IllegalStateException("Model input '" + INPUT_NAME + "' is missing or is not a tensor");
        }

        long[] shape = tensorInfo.getShape();
        if (shape.length < 3 || (shape[2] >= 0 && shape[2] != FEATURE_COUNT)) {
            throw new IllegalStateException(
                    "Model input '" + INPUT_NAME + "' has shape " + java.util.Arrays.toString(shape)
                            + " but telemetry provides " + FEATURE_COUNT + " features. "
                            + "Deploy the ONNX model exported with all seven features."
            );
        }
    }
//    The input must have this shape:
        //
        //float[1][20][7]
        //
        //1  = one server sequence being predicted
        //20 = last 20 telemetry readings
        //7  = features in each reading
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
                throw new IllegalArgumentException("Each telemetry sample must have exactly 7 features");
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
