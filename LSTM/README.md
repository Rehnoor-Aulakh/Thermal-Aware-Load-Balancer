# 🔥 Thermal-Aware CPU Temperature Prediction using LSTM

A deep learning project for forecasting CPU temperature from real-time hardware telemetry collected across multiple laptops. The objective is to predict future CPU temperatures using historical system metrics, enabling proactive thermal management and intelligent scheduling for thermal-aware load balancing systems.

---

# 📊 Prediction Results

<table>
<tr>
<td align="center">

### Master Test Set
<img src="masterTestSet.png" width="430">

</td>

<td align="center">

### Prabhsimrat Logs
<img src="prabh_logs.png" width="430">

</td>
</tr>

<tr>
<td align="center">

### Sushant Logs
<img src="sushant_logs.png" width="430">

</td>

<td align="center">

### Manan Logs
<img src="manan_logs.png" width="430">

</td>
</tr>
</table>

---

# 📑 Model Summary

| Property | Value |
|----------|-------|
| Model | Bidirectional LSTM |
| Framework | TensorFlow / Keras |
| Input Sequence Length | 20 timesteps |
| Prediction Target | Next CPU Temperature |
| Features | 7 Hardware Telemetry Features |
| Optimizer | Adam |
| Loss Function | Mean Squared Error (MSE) |
| Evaluation Metrics | MAE, RMSE, R² |
| Train/Validation/Test Split | 80% / 10% / 10% |
| Hardware Used | NVIDIA RTX 4060 Laptop GPU |

---

# 📂 Dataset

The model was trained using telemetry collected from **three different laptops**, improving hardware diversity and evaluating cross-device generalization.

| Dataset | Train | Validation | Test |
|---------|-------|------------|------|
| Prabhsimrat Logs | 13,088 | 1,636 | 1,637 |
| Sushant Logs | 12,874 | 1,609 | 1,610 |
| Manan Logs | 16,191 | 2,024 | 2,024 |

Combined master dataset:

| Split | Samples |
|-------|---------|
| Train | 42,006 |
| Validation | 5,122 |
| Test | 5,124 |

---

# 📊 Features Used

The LSTM receives the following hardware telemetry as input:

- CPU Usage
- CPU Average Clock
- CPU Package Power
- CPU Temperature
- CPU Core Maximum Temperature
- GPU Usage
- GPU Temperature

Each prediction uses the previous **20 timesteps** to forecast the next CPU temperature.

---

# 🧠 Model Architecture

```
Input Sequence (20 × 7)

        │

Bidirectional LSTM
Hidden Size = 64
2 Layers

        │

Dropout (0.1)

        │

Fully Connected Layer

        │

Predicted CPU Temperature
```

---

# ⚙️ Training Configuration

| Hyperparameter | Value |
|---------------|-------|
| Hidden Size | 64 |
| LSTM Layers | 2 |
| Bidirectional | Yes |
| Dropout | 0.10 |
| Batch Size | 32 |
| Lookback Window | 20 |
| Learning Rate | 0.00779 |
| Weight Decay | 1.125×10⁻⁶ |
| Optimizer | Adam |
| Scheduler | ReduceLROnPlateau |
| Early Stopping | Enabled |

---

# 📈 Training Performance

The training and validation losses converged smoothly without significant divergence, indicating stable optimization.

Final Training Loss

```
0.004851
```

Final Validation Loss

```
0.003789
```

Validation Gap

```
21.9%
```

This indicates only **mild overfitting**, suggesting that the model generalizes reasonably well while maintaining low prediction error.

---

# 📊 Test Performance

| Test Dataset | MAE (°C) | RMSE (°C) | R² Score |
|--------------|---------:|----------:|---------:|
| **Sushant Logs** | **2.36** | **2.67** | **-4.3402** |
| **Prabhsimrat Logs** | **8.40** | **10.89** | **0.0975** |
| **Manan Logs** | **5.19** | **7.51** | **0.3642** |

---

# 📈 Comparison with Persistence Baseline

The persistence baseline simply predicts:

```
Next Temperature = Current Temperature
```

| Dataset | LSTM MAE (°C) | Persistence MAE (°C) | Improvement |
|----------|--------------:|---------------------:|------------:|
| **Prabhsimrat** | **1.525** | **1.458** | **-4.58%** |
| **Sushant** | **1.015** | **0.994** | **-2.10%** |
| **Manan** | **1.523** | **1.477** | **-3.14%** |

> A negative improvement indicates that the persistence baseline slightly outperformed the LSTM on all three datasets.

# 📊 Statistical Analysis

To better understand the model's behavior, we compared the statistical properties of the predicted temperatures with the actual telemetry.

## Prabhsimrat Logs

| Metric | Actual | Predicted |
|---------|-------:|----------:|
| Minimum | 0.00 °C | 30.90 °C |
| Maximum | 95.60 °C | 102.15 °C |
| Mean | 85.48 °C | 85.03 °C |
| Standard Deviation | 11.46 °C | 6.92 °C |

---

## Sushant Logs

| Metric | Actual | Predicted |
|---------|-------:|----------:|
| Minimum | 87.00 °C | 89.57 °C |
| Maximum | 98.00 °C | 95.14 °C |
| Mean | 94.57 °C | 92.30 °C |
| Standard Deviation | 1.16 °C | 0.77 °C |

---

## Manan Logs

| Metric | Actual | Predicted |
|---------|-------:|----------:|
| Minimum | 67.00 °C | 70.33 °C |
| Maximum | 99.00 °C | 95.83 °C |
| Mean | 90.07 °C | 88.30 °C |
| Standard Deviation | 9.41 °C | 7.17 °C |

# 📖 Interpretation

The proposed Bidirectional LSTM successfully captures the overall thermal behaviour of the processor and predicts average operating temperatures with good accuracy across multiple devices.

Statistical analysis shows that the predicted means closely match the actual means for all datasets, indicating that the network has learned the general thermal operating region of each processor.

However, the predicted standard deviations are consistently lower than the actual values, demonstrating that the model smooths rapid temperature fluctuations and struggles to reproduce sudden thermal spikes.

Although the prediction curves closely follow the overall trend, comparison against the persistence baseline reveals that one-step forecasting does not yet outperform the simple strategy of predicting the next temperature as the current temperature.

This suggests that adjacent telemetry samples are highly correlated, making persistence an exceptionally strong baseline for very short-term prediction.

# ⚠️ Current Limitation

The current model predicts only the immediate next CPU temperature:

```
Previous 20 Telemetry Samples
            │
            ▼
     Next Temperature
```

Because CPU temperatures change only slightly between adjacent telemetry samples, the persistence baseline becomes extremely competitive, leaving limited opportunity for the LSTM to improve upon it.
---

# 🚀 Future Work

The next phase of this project will focus on **long-horizon forecasting** instead of one-step prediction.

Rather than predicting the immediate next reading, the model will forecast CPU temperatures **30–60 seconds into the future**, providing sufficient advance warning for thermal-aware scheduling and workload migration.

Future improvements include:

- Multi-step temperature forecasting
- Prediction horizon of 30–60 seconds
- Additional hardware telemetry features
- Transformer-based sequence models
- Cross-device domain adaptation
- Online continual learning
- Integration with the Thermal-Aware Load Balancer
---

# 📌 Conclusion

The proposed Bidirectional LSTM successfully learns relationships between CPU workload and temperature across multiple hardware platforms. However, comparison with the persistence baseline shows that one-step temperature forecasting does not provide sufficient predictive advantage.

This insight is valuable because it motivates the transition toward longer-horizon forecasting, where deep learning models are expected to outperform simple heuristic approaches and provide practical benefits for intelligent thermal-aware load balancing systems.