# 🔥 Thermal-Aware CPU Temperature Prediction using LSTM

A deep learning project for forecasting CPU temperature from real-time hardware telemetry collected across multiple laptops. The objective is to predict future CPU temperatures using historical system metrics, enabling proactive thermal management and intelligent scheduling for thermal-aware load balancing systems.

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
# 📑 Models Implemented

The repository contains multiple deep learning architectures for CPU temperature prediction. Each model has been evaluated on different combinations of hardware telemetry datasets to study cross-device generalization.

| Model | Description |
|--------|-------------|
| LSTM | Baseline recurrent neural network for sequence modeling |
| GRU | Gated Recurrent Unit network with fewer parameters than LSTM |
| CNN + LSTM | Convolutional feature extraction followed by an LSTM |
| CNN + GRU | Convolutional feature extraction followed by a GRU |
| LSTM + Multi-Head Attention | LSTM enhanced with Multi-Head Attention to capture long-range temporal dependencies |

# 📂 Datasets

The experiments were conducted using hardware telemetry collected from **five different laptops**, enabling evaluation of cross-device generalization across multiple hardware configurations.

| Dataset | Samples | Features |
|---------|--------:|---------:|
| Manan | 14,803 | 13 |
| Mukul | 18,890 | 13 |
| Prabhsimrat | 15,365 | 13 |
| Prabh2 | 14,513 | 13 |
| Arshnoor | 14,257 | 13 |

**Total telemetry samples:** **77,828**

The notebooks in this repository evaluate different combinations of these datasets to study how increasing hardware diversity impacts model generalization and prediction performance.


# 📊 Model Comparison

The following table summarizes all experiments conducted across different dataset combinations.

| Model | Datasets Used | MAE (°C) | RMSE (°C) | R² Score |
|--------|---------------|---------:|----------:|---------:|
| **LSTM** | Manan, Mukul, Prabh | **2.492** | **4.940** | **0.8844** |
| **LSTM** | Manan, Mukul, Prabh2 | **2.283** | **4.828** | **0.8868** |
| **LSTM** | Manan, Mukul, Prabh2, Arshnoor | **2.600** | **5.162** | **0.8677** |
| **GRU** | Manan, Mukul, Prabhsimrat | **2.457** | **4.873** | **0.8875** |
| **GRU** | Manan, Mukul, Prabh2 | **2.259** | **4.812** | **0.8875** |
| **GRU** | Manan, Mukul, Prabh2, Arshnoor | **2.547** | **5.101** | **0.8708** |
| **CNN + GRU** | Manan, Mukul, Prabh2 | **2.231** | **4.774** | **0.8893** |
| **CNN + GRU** | Manan, Mukul, Prabh2, Arshnoor | **2.481** | **5.034** | **0.8742** |
| **CNN + LSTM** | Manan, Mukul, Prabh2 | **2.256** | **4.806** | **0.8878** |
| **LSTM + Multi-Head Attention** | Manan, Mukul, Prabh2 | **2.398** | **4.984** | **0.8793** |

# 📈 Persistence Baseline Comparison

The persistence baseline predicts the next CPU temperature by assuming that the next reading is equal to the current temperature. The table below summarizes the baseline performance for each experimental setup.

| Model | Datasets Used | MAE (°C) | RMSE (°C) | R² Score |
|--------|---------------|---------:|----------:|---------:|
| **LSTM** | Manan, Mukul, Prabh | 2.576 | 5.120 | 0.8759 |
| **LSTM** | Manan, Mukul, Prabh2 | 2.402 | 5.045 | 0.8764 |
| **LSTM** | Manan, Mukul, Prabh2, Arshnoor | 2.797 | 5.533 | 0.8480 |
| **GRU** | Manan, Mukul, Prabhsimrat | 2.576 | 5.120 | 0.8759 |
| **GRU** | Manan, Mukul, Prabh2 | 2.402 | 5.045 | 0.8764 |
| **GRU** | Manan, Mukul, Prabh2, Arshnoor | 2.797 | 5.533 | 0.8480 |
| **CNN + GRU** | Manan, Mukul, Prabh2 | 2.402 | 5.045 | 0.8764 |
| **CNN + GRU** | Manan, Mukul, Prabh2, Arshnoor | 2.797 | 5.533 | 0.8480 |
| **CNN + LSTM** | Manan, Mukul, Prabh2 | 2.402 | 5.045 | 0.8764 |
| **LSTM + Multi-Head Attention** | Manan, Mukul, Prabh2 | 2.402 | 5.045 | 0.8764 |

---

### 📌 Observation

Across all experiments, the persistence baseline remains a strong benchmark for short-term (one-step) CPU temperature prediction due to the high temporal correlation between consecutive telemetry samples. While several deep learning models outperform the baseline, comparing against persistence provides a consistent reference for evaluating improvements across different architectures and dataset combinations.


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



# 🏆 Best Performing Models

| Category | Model | MAE (°C) | RMSE (°C) | R² Score |
|----------|-------|---------:|----------:|---------:|
| **Best Overall** | CNN + GRU (Manan, Mukul, Prabh2) | **2.231** | **4.774** | **0.8893** |
| **Best LSTM** | LSTM (Manan, Mukul, Prabh2) | **2.283** | **4.828** | **0.8868** |
| **Best GRU** | GRU (Manan, Mukul, Prabh2) | **2.259** | **4.812** | **0.8875** |
| **Best CNN + LSTM** | CNN + LSTM (Manan, Mukul, Prabh2) | **2.256** | **4.806** | **0.8878** |
| **Best Attention Model** | LSTM + Multi-Head Attention (Manan, Mukul, Prabh2) | **2.398** | **4.984** | **0.8793** |

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

This repository presents a comprehensive evaluation of multiple deep learning architectures for CPU temperature prediction using cross-device hardware telemetry.

The implemented models include **LSTM, GRU, CNN + LSTM, CNN + GRU, and LSTM with Multi-Head Attention**, each trained and evaluated on different combinations of telemetry collected from multiple laptops.

Among all evaluated architectures, the **CNN + GRU model trained on the Manan, Mukul, and Prabh2 datasets achieved the best overall performance**, with:

- **MAE:** 2.231 °C
- **RMSE:** 4.774 °C
- **R² Score:** 0.8893

These experiments demonstrate the effectiveness of hybrid deep learning architectures and diverse training datasets for CPU temperature prediction, providing a strong foundation for future thermal-aware scheduling and intelligent load balancing systems.