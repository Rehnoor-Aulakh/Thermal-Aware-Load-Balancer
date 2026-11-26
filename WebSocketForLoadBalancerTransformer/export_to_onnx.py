"""
Export PatchTSTForRegression to ONNX format.

Usage:
    python export_to_onnx.py

Produces:
    patchtst_regression_best_1sec.onnx   — the ONNX model
"""

import numpy as np
import torch
from transformers import PatchTSTConfig, PatchTSTForRegression

# ── Paths ────────────────────────────────────────────────────────────
PT_CHECKPOINT = "patchtst_regression_best_1sec.pt"
ONNX_OUTPUT   = "patchtst_regression_best_1sec.onnx"

# ── Model constants (must match training notebook) ───────────────────
FEATURE_COLS = [
    "cpuUsage",
    "cpuPackagePower",
    "gpuCoreTemperature",
    "gpuHotspotTemperature",
    "cpuEfficiencyAverageClock",
    "targetLoad",
    "cpuTemperature",
]
LOOKBACK = 13   # context_length (26s / 2s interval)
NUM_CHANNELS = len(FEATURE_COLS)  # 7


# ── 1.  Rebuild the model from the saved config + weights ───────────
print("Loading checkpoint …")
ckpt = torch.load(PT_CHECKPOINT, map_location="cpu", weights_only=False)

# The checkpoint stores the config dict alongside the state dict
config = PatchTSTConfig(**ckpt["config"])
model  = PatchTSTForRegression(config)
model.load_state_dict(ckpt["model_state"])
model.eval()

print(f"Model loaded — {sum(p.numel() for p in model.parameters()):,} parameters")


# ── 2.  Thin wrapper: ONNX needs plain tensors, not dataclasses ─────
class PatchTSTOnnxWrapper(torch.nn.Module):
    """
    Wraps PatchTSTForRegression so that:
      input:  past_values  — float32  (batch, 13, 7)
      output: predicted_dT — float32  (batch, 1)
    """
    def __init__(self, hf_model):
        super().__init__()
        self.hf_model = hf_model

    def forward(self, past_values: torch.Tensor) -> torch.Tensor:
        out = self.hf_model(past_values=past_values)
        return out.regression_outputs          # shape (batch, 1)


wrapper = PatchTSTOnnxWrapper(model)
wrapper.eval()


# ── 3.  Export to ONNX ──────────────────────────────────────────────
dummy_input = torch.randn(1, LOOKBACK, NUM_CHANNELS, dtype=torch.float32)

print(f"Exporting to {ONNX_OUTPUT} …")
torch.onnx.export(
    wrapper,
    (dummy_input,),
    ONNX_OUTPUT,
    input_names=["past_values"],
    output_names=["predicted_dT"],
    dynamic_axes={
        "past_values":  {0: "batch_size"},
        "predicted_dT": {0: "batch_size"},
    },
    opset_version=17,
    do_constant_folding=True,
)
print(f"✅  ONNX model saved → {ONNX_OUTPUT}")


# ── 4.  Validate: compare PyTorch vs ONNX outputs ──────────────────
try:
    import onnxruntime as ort

    print("\nValidating ONNX output against PyTorch …")
    sess = ort.InferenceSession(ONNX_OUTPUT)

    # Use a few random inputs
    for i in range(5):
        test_input = np.random.randn(1, LOOKBACK, NUM_CHANNELS).astype(np.float32)

        # PyTorch
        with torch.no_grad():
            pt_out = wrapper(torch.from_numpy(test_input)).numpy()

        # ONNX Runtime
        onnx_out = sess.run(["predicted_dT"], {"past_values": test_input})[0]

        max_diff = np.max(np.abs(pt_out - onnx_out))
        print(f"  Sample {i+1}: max |diff| = {max_diff:.8f}")
        assert max_diff < 1e-4, f"Mismatch too large: {max_diff}"

    print("\n✅  Validation passed — ONNX outputs match PyTorch within tolerance.")

except ImportError:
    print("\n⚠️  onnxruntime not installed — skipping validation.")
    print("   Install with:  pip install onnxruntime")
