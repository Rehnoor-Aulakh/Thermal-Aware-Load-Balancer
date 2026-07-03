
# 🌡️ CPU Temperature Prediction using LSTM

---

# 📈 Prediction Results

<p align="center">
  <img src="./prediction.jpg" alt="Prediction Graph" width="900"/>
</p>


---

# 📖 Overview

This module implements a **Stacked Long Short-Term Memory (LSTM)** regression model for forecasting CPU temperature using sequential hardware telemetry collected from a physical system.

Telemetry was generated using a custom workload generator together with **Libre Hardware Monitor** and **OSHI**, enabling the model to learn temporal relationships between processor activity and thermal response.

The prediction engine is intended for proactive thermal monitoring, intelligent scheduling, anomaly detection, and overheating prevention.

---

# 🏗️ Model Pipeline

```text
Hardware Stress Generator
          │
          ▼
Telemetry Collection
(OSHI + Libre Hardware Monitor)
          │
          ▼
Data Preprocessing
(Normalization + Windowing)
          │
          ▼
Stacked LSTM Network
          │
          ▼
CPU Temperature Prediction
```

---

# ⚙️ Model Details

| Component | Configuration |
|-----------|---------------|
| **Model** | Stacked LSTM Regressor |
| **Framework** | TensorFlow / Keras |
| **Optimizer** | Adam |
| **Loss Function** | Mean Squared Error (MSE) |
| **Learning Rate** | 0.001 |
| **Epochs** | 50 |
| **Batch Size** | 32 |
| **Sequence Length** | 30 Timesteps |
| **Target Variable** | CPU Temperature |
| **Prediction Type** | Time-Series Regression |

---

# 📊 Evaluation Metrics

| Metric | Value | Interpretation |
|-------|------:|----------------|
| **MAE** | **2.02 °C** | Average prediction error across the test set. |
| **RMSE** | **5.14 °C** | Larger errors are penalized more heavily; indicates occasional misses during sudden thermal spikes. |
| **R² Score** | **0.8045** | Explains **80.45%** of the variance in CPU temperature. |

---

## 📈 Mean Absolute Error (MAE)

The model predicts CPU temperature with an **average error of only 2.02 °C**, demonstrating stable performance across most workload conditions.

## 📉 Root Mean Squared Error (RMSE)

The RMSE of **5.14 °C** indicates that while the model accurately follows gradual thermal changes, it occasionally underestimates abrupt temperature spikes caused by rapid workload transitions.

## 📊 R² Score

An **R² score of 0.8045** confirms that the model captures a significant portion of the relationship between workload characteristics and CPU thermal behaviour, making it reliable for real-world telemetry prediction.

---

# 📂 Dataset Features

| Feature |
|---------|
| CPU Usage |
| CPU Temperature |
| CPU Package Power |
| CPU Average Clock |
| GPU Usage |
| GPU Temperature |
| GPU Memory Usage |
| RAM Usage |
| Process Count |
| Network Connections |
| Timestamp |

---

# 📌 Conclusion

The proposed LSTM model successfully learns temporal dependencies from hardware telemetry and predicts CPU temperature with high accuracy. The achieved **MAE of 2.02 °C** and **R² score of 0.8045** demonstrate that the model can effectively model real-world thermal behaviour, making it suitable for predictive monitoring and intelligent system management.
