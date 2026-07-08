import numpy as np
import tensorflow as tf
import tf2onnx
import onnxruntime as ort


KERAS_MODEL_PATH = "best_cross_device_lstm.keras"
ONNX_MODEL_PATH = "cross_device_lstm.onnx"


# ---------------------------------------------------------
# 1. LOAD KERAS MODEL
# ---------------------------------------------------------

print("\nLoading Keras model...")

keras_model = tf.keras.models.load_model(
    KERAS_MODEL_PATH,
    compile=False
)

print("Keras model loaded successfully.")
print("Input shape:", keras_model.input_shape)
print("Output shape:", keras_model.output_shape)


# ---------------------------------------------------------
# 2. CREATE EXPLICIT INFERENCE FUNCTION
# ---------------------------------------------------------

input_signature = [
    tf.TensorSpec(
        shape=(None, 20, 6),
        dtype=tf.float32,
        name="telemetry_input"
    )
]


@tf.function(
    input_signature=input_signature
)
def inference_function(telemetry_input):

    prediction = keras_model(
        telemetry_input,
        training=False
    )

    return {
        "predicted_delta_t": prediction
    }


# ---------------------------------------------------------
# 3. CONVERT FUNCTION TO ONNX
# ---------------------------------------------------------

print("\nConverting model to ONNX...")

tf2onnx.convert.from_function(
    inference_function,
    input_signature=input_signature,
    opset=17,
    output_path=ONNX_MODEL_PATH
)

print("ONNX model saved successfully.")
print("Saved at:", ONNX_MODEL_PATH)


# ---------------------------------------------------------
# 4. CREATE IDENTICAL TEST INPUT
# ---------------------------------------------------------

np.random.seed(42)

test_input = np.random.randn(
    1,
    20,
    6
).astype(np.float32)

print("\nTest input shape:", test_input.shape)


# ---------------------------------------------------------
# 5. KERAS PREDICTION
# ---------------------------------------------------------

keras_prediction = keras_model.predict(
    test_input,
    verbose=0
)

keras_delta_t = float(
    keras_prediction[0][0]
)


# ---------------------------------------------------------
# 6. ONNX PREDICTION
# ---------------------------------------------------------

onnx_session = ort.InferenceSession(
    ONNX_MODEL_PATH,
    providers=["CPUExecutionProvider"]
)

onnx_input = onnx_session.get_inputs()[0]
onnx_output = onnx_session.get_outputs()[0]

print("\nONNX input:")
print("Name :", onnx_input.name)
print("Shape:", onnx_input.shape)
print("Type :", onnx_input.type)

print("\nONNX output:")
print("Name :", onnx_output.name)
print("Shape:", onnx_output.shape)
print("Type :", onnx_output.type)


onnx_prediction = onnx_session.run(
    None,
    {
        onnx_input.name: test_input
    }
)


onnx_delta_t = float(
    np.asarray(onnx_prediction[0]).reshape(-1)[0]
)


# ---------------------------------------------------------
# 7. COMPARE
# ---------------------------------------------------------

difference = abs(
    keras_delta_t - onnx_delta_t
)

print("\n========================================")
print("       CONVERSION VALIDATION RESULT")
print("========================================")

print(f"Keras Delta T : {keras_delta_t:.10f}")
print(f"ONNX Delta T  : {onnx_delta_t:.10f}")
print(f"Difference    : {difference:.10f}")


if difference < 1e-4:

    print(
        "\nSUCCESS: ONNX model matches Keras model."
    )

else:

    print(
        "\nWARNING: Predictions differ more than expected."
    )