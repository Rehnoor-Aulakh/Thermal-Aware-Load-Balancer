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
import jakarta.annotation.PreDestroy;

@Service
public class TemperaturePredictionService {
    private static final String MODEL_PATH = "models/patchtst_regression_best_1sec.onnx";
    private static final String INPUT_NAME = "past_values";

    private final OrtEnvironment environment;
    private final OrtSession session;

    private static final int TIMESTEPS = 39;
    private static final int FEATURE_COUNT = 7;

    // Feature order: cpuUsage, cpuPackagePower, gpuCoreTemperature,
    //                gpuHotspotTemperature, cpuEfficiencyAverageClock,
    //                targetLoad, cpuTemperature
    // TODO: Replace placeholder values with actual means from
    //       cross_device_feature_scaler_transformer_1sec.pkl (scaler_X.mean_)
    private static final double[] FEATURE_MEANS = {

            49.40086543,

            28.12757966,

            52.65764165,

            59.67455542,

            1828.58544065,

            43.82835284,

            82.05555405

    };

    // TODO: Replace placeholder values with actual scales from
    //       cross_device_feature_scaler_transformer_1sec.pkl (scaler_X.scale_)
    private static final double[] FEATURE_SCALES = {

            34.50007588,

            16.02660278,

            7.62116998,

            7.77169684,

            995.10887655,

            35.85509557,

            15.52799240

    };
    public TemperaturePredictionService() throws IOException, OrtException {
        System.out.println("Loading PatchTST Transformer Temperature Prediction Model...");

        environment= OrtEnvironment.getEnvironment();
        ClassPathResource modelResource = new ClassPathResource(MODEL_PATH);

        // Load from file path (not bytes) so ONNX Runtime can resolve
        // the external data file (patchtst_regression_best_1sec.onnx.data)
        // relative to the model's on-disk location.
        String modelAbsolutePath = modelResource.getFile().getAbsolutePath();

        session = environment.createSession(modelAbsolutePath, new OrtSession.SessionOptions());

        System.out.println("ONNX Model Loaded Successfully!!!");

        System.out.println("Model inputs: " + session.getInputInfo().keySet());

        System.out.println("Model outputs: "+ session.getOutputInfo().keySet());


    }
//    The input must have this shape:
        //
        //float[1][39][7]
        //
        //1  = one server sequence being predicted
        //39 = last 39 telemetry readings (39s lookback / 1s interval)
        //7  = features in each reading
//    modelInput
//│
//        └── [0]                         one batch
//    ├── [0]  → first reading
//    ├── [1]  → second reading
//    ├── ...
//            └── [38] → latest reading
    public float predict(float[][][] modelInput) throws OrtException{
        try(
             // OnnxTensor.createTensor(environment, modelInput) converts the normal Java multidimensional array into the tensor format ONNX Runtime understands.
                OnnxTensor inputTensor = OnnxTensor.createTensor(environment, modelInput);
                OrtSession.Result result = session.run(Map.of(INPUT_NAME,inputTensor))
             //  sends the tensor into the loaded model under the exact input name we verified
        ){
//            past_values → PatchTST Transformer → predicted_dT
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
            throw new IllegalArgumentException("Expected exactly " + TIMESTEPS + " telemetry samples but received "+ rawSequence.length);
        }

        float[][][] modelInput = new float[1][TIMESTEPS][FEATURE_COUNT];

        for(int timestep = 0; timestep< TIMESTEPS; timestep++){
            if(rawSequence[timestep].length!=FEATURE_COUNT){
                throw new IllegalArgumentException("Each telemetry sample must have exactly " + FEATURE_COUNT + " features");
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
