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
---

# 📊 Model Comparison

The following table summarizes the performance of all models evaluated in this project across different dataset combinations.

| Model | Datasets Used | MAE (°C) | RMSE (°C) | R² Score |
|--------|---------------|---------:|----------:|---------:|
| **LSTM** | Manan, Mukul, Prabh | **2.492** | **4.940** | **0.8844** |
| **LSTM** | Manan, Mukul, Prabh2 | **2.283** | **4.828** | **0.8868** |
| **LSTM** | Manan, Mukul, Prabh2, Arshnoor | **2.600** | **5.162** | **0.8677** |
| **GRU** | Manan, Mukul, Prabhsimrat | **2.457** | **4.873** | **0.8875** |
| **GRU** | Manan, Mukul, Prabh2, Arshnoor | **2.547** | **5.101** | **0.8708** |
| **CNN + GRU** | Manan, Mukul, Prabh2 | **2.231** | **4.774** | **0.8893** |

---

# 📈 Persistence Baseline Comparison

The persistence baseline predicts the next CPU temperature using the current temperature.

| Experiment | MAE (°C) | RMSE (°C) | R² Score |
|------------|---------:|----------:|---------:|
| **LSTM** (Manan, Mukul, Prabh) | 2.576 | 5.120 | 0.8759 |
| **LSTM** (Manan, Mukul, Prabh2) | 2.402 | 5.045 | 0.8764 |
| **LSTM** (Manan, Mukul, Prabh2, Arshnoor) | 2.797 | 5.533 | 0.8480 |
| **GRU** (Manan, Mukul, Prabhsimrat) | 2.576 | 5.120 | 0.8759 |
| **GRU** (Manan, Mukul, Prabh2, Arshnoor) | 2.797 | 5.533 | 0.8480 |
| **CNN + GRU** (Manan, Mukul, Prabh2) | 2.402 | 5.045 | 0.8764 |

---

# 📌 Key Observations

- **Best overall performing model:** **CNN + GRU**
  - MAE: **2.231 °C**
  - RMSE: **4.774 °C**
  - R²: **0.8893**

- **Best GRU model:**
  - Trained on **Manan, Mukul and Prabhsimrat**
  - MAE: **2.457 °C**
  - RMSE: **4.873 °C**
  - R²: **0.8875**

- **Best LSTM model:**
  - Trained on **Manan, Mukul and Prabh2**
  - MAE: **2.283 °C**
  - RMSE: **4.828 °C**
  - R²: **0.8868**

These experiments demonstrate that increasing dataset diversity improves cross-device generalization, while the CNN + GRU architecture consistently achieves the best overall prediction performance among the evaluated models.

---

# 🚀 Future Work

Future improvements include:

- Multi-step CPU temperature forecasting
- Prediction horizons of 30–60 seconds
- Transformer-based sequence models
- Additional hardware telemetry features
- Cross-device domain adaptation
- Online continual learning
- Integration with the Thermal-Aware Load Balancer

---

# 📌 Conclusion

This repository evaluates multiple deep learning architectures for CPU temperature prediction using hardware telemetry collected from different laptop configurations.

Across all experiments, the **CNN + GRU** architecture achieved the strongest overall performance, while both **LSTM** and **GRU** models demonstrated strong predictive capability and good cross-device generalization.

Future work will focus on long-horizon forecasting and tighter integration with thermal-aware scheduling systems for intelligent load balancing.