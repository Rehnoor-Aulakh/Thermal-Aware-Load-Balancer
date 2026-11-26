# 🌡️ Thermal-Aware Load Balancer

A distributed system that collects live hardware telemetry from several servers, **predicts each server's CPU temperature about 10 seconds ahead** with a deep learning model, and ranks the servers by a **Thermal Score** so that the coolest, least stressed server can be chosen next.

Computer Science capstone project.

---

## 📌 At a Glance

| Item | Value |
|---|---|
| Telemetry collection interval | every **2 seconds** |
| Sending to the load balancer | **immediately** after each sample, over WebSocket |
| History the model reads | **20 samples ≈ 40 seconds** |
| Prediction horizon | **10 seconds ahead** (5 samples) |
| Time before the first prediction | about **40 seconds** after connecting |
| Prediction frequency after that | one per sample (about every 2 seconds) |
| Deployed model | **CNN + GRU** (ONNX, runs inside Java) |
| Test accuracy | **MAE 2.203 °C, RMSE 4.809 °C, R² 0.8921** |
| Training data | **73,711 samples** from **5 laptops** |

---

## 🎯 Problem and Idea

Traditional load balancers choose a server by request count, connections or CPU usage. None of these says how hot a machine is **about to become**. A server can look lightly loaded while still being hot, or be heating up quickly.

This project adds temperature, and a short-term temperature forecast, to the decision, so that work can be steered away from a machine **before** it overheats.

---

## 🏗️ Architecture

```text
 BACKEND SERVER (one per laptop)
 ┌──────────────────────────────────────────────────────────┐
 │ LibreHardwareMonitor  (web server :8085)                 │
 │          │ read every 2 s                                │
 │          ▼                                               │
 │ BackendServerAgent                                       │
 │   • Telemetry WebSocket  :8086/telemetry                 │
 │   • Stress API (HTTP)    :8080/stress/target-load        │
 └──────────┬───────────────────────────────▲───────────────┘
            │ telemetry (JSON)              │ target CPU load
            ▼                               │
 LOAD BALANCER  (Spring Boot, :8080)        │
 ┌──────────────────────────────────────────┴───────────────┐
 │ 1. Validate the sample                                   │
 │ 2. Keep the last 20 samples per server                   │
 │ 3. Scale them and run the CNN + GRU model  →  ΔT         │
 │ 4. Predicted temp = current temp + ΔT                    │
 │ 5. Thermal Score  →  ranked priority queue               │
 └──────────┬───────────────────────────────────────────────┘
            │ WebSocket + REST
            ▼
 REACT DASHBOARD  (Vite, :5173)
   Load Balancer  |  Prediction Demo  |  Server Health Demo
```

Machines communicate over a local network or the **Tailscale** VPN.

---

## ⏱️ Timing

| Event | Duration | Where it is set |
|---|---|---|
| Collect one telemetry sample | 2,000 ms after the previous one finishes | `telemetry.interval-ms` in the agent's `application.properties` |
| Save the sample to `system_logs.jsonl` | every sample | `TelemetryService` |
| Send the sample to the load balancer | straight after saving it | `TelemetryService` |
| Fill the model's window | 20 samples ≈ 40 s | `TelemetryPredictionManager` |
| Forecast horizon | 10 s | training notebook; `PredictionEvaluationService` |
| Check a forecast against reality | first sample at or after +10 s | `PredictionEvaluationService`, dashboard `forecast.js` |
| Declare a server silent | 10 s without data | `BackendConnectionManager` |
| Refresh the priority queue on screen | every 1 s | Server Health Demo |

The training data was sampled at a median interval of about 2.02 to 2.04 s, which matches the live 2 s setting. **The two must stay matched**: changing the interval changes what "20 samples" and "5 samples ahead" mean to the model.

---

## 🔄 How One Prediction Is Made

1. **Collect.** The agent reads LibreHardwareMonitor (temperature, power, clocks, loads) and OSHI (memory, processes, network).
2. **Send.** The JSON sample is pushed to the load balancer.
3. **Validate.** A sample with missing or impossible CPU readings is rejected, and the server leaves the queue.
4. **Buffer.** The last 20 valid samples are kept for each server.
5. **Scale.** Each feature becomes `(value − mean) / scale`, using the values from training.
6. **Predict.** The model returns **ΔT**, the expected temperature change over 10 s.
7. **Score and rank.** The lowest Thermal Score goes to the top of the queue.

### Model inputs (7 features, in this order)

| # | Feature | Unit |
|---|---|---|
| 1 | `cpuUsage` | % |
| 2 | `cpuPackagePower` | W |
| 3 | `gpuCoreTemperature` | °C |
| 4 | `gpuHotspotTemperature` | °C |
| 5 | `cpuEfficiencyAverageClock` | MHz |
| 6 | `targetLoad` | % (load requested from the stress service) |
| 7 | `cpuTemperature` | °C |

Input shape: `[1, 20, 7]`. Output: one number, ΔT.

**Why predict ΔT instead of the temperature?** Temperature changes little from one moment to the next. Predicting the *change* makes the model learn the heating or cooling trend rather than repeat the current value.

### Thermal Score (lower is better)

```text
Score = 0.45 × Predicted CPU Temperature
      + 0.25 × CPU Usage
      + 0.15 × CPU Package Power
      + 0.10 × GPU Core Temperature
      + 0.05 × Target Load
```

The weights are chosen by design, with predicted temperature counting most. They are not learned from data.

### Worked example (one window from Manan's dataset)

Current temperature 86.00 °C, model output ΔT = +8.81 °C, so the predicted temperature is 94.81 °C. The reading 10 s later was 94.00 °C.

`0.45×94.81 + 0.25×83 + 0.15×53.2 + 0.10×51 + 0.05×80 = 80.50`

This window was picked to illustrate the calculation; it is more accurate than average.

---

## 📂 Datasets

Collected on five laptops over about **8 to 9 hours each**, in July 2026.

| Dataset | Samples | Recorded duration | File in `Models/merged_experiments/` |
|---|---:|---:|---|
| Manan | 15,275 | 8.6 h | `manan_merged_experiment.csv` |
| Prabhsimrat | 14,291 | 8.1 h | `prabh_merged_experiment.csv` |
| Sushant | 14,133 | 8.1 h | `sushant_merged_experiment.csv` |
| Gursimar | 14,621 | 8.2 h | `gursimar_merged_experiment.csv` |
| Gurvivek | 15,391 | 8.8 h | `gurvivek_merged_experiment.csv` |
| **Total** | **73,711** | | |

Each row holds the telemetry plus the experiment phase and target load. `Models/wrong_datasets/` holds data that was excluded.

### Stress experiment used to collect the data

The `Stress Generator` runs a fixed schedule so that every laptop goes through the same conditions:

| Phase | Planned duration | What it does |
|---|---|---|
| Initial idle | 2 min | Baseline |
| Ramp | 74 min | Load steps up from 0% to 100% and back |
| Chaos | 74 min | Random load jumps |
| Transition | 74 min | Sudden switches between pairs, e.g. 0 → 100 |
| Mixed | 148 min | Combination of ramp, chaos and transition |
| GPU + CPU combined | 30 + 3 × 15 min | Both processors loaded, run after each main phase |
| Cooling | 7 × 5 min, then 10 min | Recovery between phases and at the end |

---

## 🤖 Models and Accuracy

### Training setup (CNN + GRU and CNN + LSTM)

```text
Input 20 × 7
  → Conv1D (32 filters, kernel 3, ReLU)
  → MaxPooling (2)
  → GRU or LSTM (40 units)
  → Dropout 0.3
  → Dense 16 (ReLU) → Dropout 0.2
  → Dense 1  (ΔT)
```

| Setting | Value |
|---|---|
| Parameters | 10,257 (GRU), 13,057 (LSTM) |
| Loss | Huber (δ = 2) |
| Optimiser | Adam, learning rate 0.0008, gradient clip norm 1.0 |
| Batch size | 32 |
| Callbacks | Early stopping (patience 10), reduce learning rate on plateau, best checkpoint |
| Split | 10-minute blocks, **70 / 15 / 15** train / validation / test, balanced by laptop and phase |
| Sequences | 46,626 train, 9,251 validation, 10,726 test |
| Cleaning | Implausible readings removed; training targets clipped to the 1st–99th percentile |

Whole **blocks** are split, not individual rows, so that almost identical neighbouring samples cannot appear in both training and testing. The scaler is fitted on training blocks only.

### Results on the test set

| Model | MAE (°C) | RMSE (°C) | R² |
|---|---:|---:|---:|
| Persistence baseline ("temperature will not change") | 2.795 | 5.648 | 0.8512 |
| CNN + LSTM | 2.210 | 4.825 | 0.8914 |
| **CNN + GRU (deployed)** | **2.203** | **4.809** | **0.8921** |

The CNN + GRU model has about **21% lower MAE** and **15% lower RMSE** than the baseline.

### Accuracy by experiment phase (CNN + GRU, MAE in °C)

| Phase | Test samples | Baseline | CNN + GRU |
|---|---:|---:|---:|
| Chaos | 1,353 | 2.99 | 2.43 |
| Cooling | 619 | 2.64 | 1.72 |
| Mixed | 4,033 | 3.04 | 2.26 |
| Ramp | 1,358 | 2.45 | 2.13 |
| Transition | 1,355 | 2.97 | 2.40 |
| Other | 2,008 | 2.33 | 2.01 |

### What the metrics mean

- **MAE:** the average size of the error, in °C.
- **RMSE:** like MAE, but large errors count more.
- **R²:** the share of temperature variation explained; 1.0 is perfect.
- **Persistence baseline:** the bar to beat. Its R² is already 0.85, so compare models against it rather than quoting R² alone.

### Experimental: Transformer (PatchTST)

`WebSocketForLoadBalancerTransformer` is a second version of the load balancer using a PatchTST Transformer. The notebook in that folder reports, on **four** laptops with a different set of recordings:

| Model | MAE (°C) | RMSE (°C) | R² |
|---|---:|---:|---:|
| Persistence baseline | 2.179 | 4.577 | 0.9132 |
| PatchTST (101,761 parameters) | 1.725 | 3.654 | 0.9447 |

> ⚠️ These numbers **cannot be compared directly** with the table above, because the data differs. They also do not describe the bundled model: the notebook uses a 13-sample window, while the deployed ONNX file expects 39, and the scaler values in the Java code differ from the saved `.pkl` file. Treat this version as experimental until the matching notebook and scaler are restored. The CNN + GRU version is the one to demonstrate.

---

## 🗂️ Project Structure

```text
Thermal-Aware-Load-Balancer/
├── BackendServerAgent/              Runs on every backend server
│   └── src/main/java/com/rehnoor/backendserveragent/
│       ├── controller/              StressController (target-load API)
│       ├── dto/                     TargetLoadRequest
│       ├── model/                   SystemLog (one telemetry sample)
│       ├── service/                 TelemetryService, StressService, TelemetryWebSocketServer
│       └── utils/                   TelemetryCollector, JsonLogger
│
├── WebSocketForLoadBalancer/        The load balancer (CNN + GRU)
│   └── src/main/
│       ├── java/com/rehnoor/websocketforloadbalancer/
│       │   ├── config/              WebSocketConfig
│       │   ├── controller/          LoadBalancerController, StressProxyController
│       │   ├── service/             TelemetryProcessingService, TelemetryPredictionManager,
│       │   │                        TemperaturePredictionService, PredictionEvaluationService,
│       │   │                        ServerHealthManager, StressProxyService
│       │   ├── model/               TelemetrySample, PendingPrediction,
│       │   │                        PredictionEvaluation, ProcessedTelemetry
│       │   └── websocket/           LoadBalancerWebSocketHandler, BackendConnectionManager,
│       │                            FrontendSessionMessenger
│       └── resources/models/        ONNX models
│
├── WebSocketForLoadBalancerTransformer/   Same structure, PatchTST model (experimental)
│
├── Load Balancer Frontend/          React + Vite dashboard
│   └── src/
│       ├── components/              Pages, tables and cards
│       ├── hooks/                   useTelemetryTabs (WebSocket handling)
│       └── utils/                   forecast.js (forecast vs. actual)
│
├── Models/                          Datasets, notebooks, trained models, scalers
├── Stress Generator/                CPU and GPU load schedule for data collection
├── ThermalTelemetryCollector/       Earlier standalone collector (the agent replaces it for live use)
├── Prediction Service/              Earlier model conversion experiments
├── HardwareMonitor.zip              LibreHardwareMonitor
└── requirements.txt                 Python packages
```

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Backend agent | Java 17, Spring Boot 4.1, OSHI, Java-WebSocket |
| Load balancer | Java 17, Spring Boot 4.1, ONNX Runtime 1.27 |
| Dashboard | React 19, Vite 8, Tailwind CSS 4 |
| Machine learning | Python, TensorFlow/Keras, PyTorch (Transformer), scikit-learn, tf2onnx |
| Hardware sensors | LibreHardwareMonitor |
| Networking | WebSocket, REST, Tailscale |

---

## ▶️ How to Run the Project

### Requirements

| Machine | Needs |
|---|---|
| Each backend server | Windows (for the bundled LibreHardwareMonitor), JDK 17 to 22 recommended (see below) |
| Load balancer and dashboard | JDK 17 or later, Node.js 20.19+ or 22.12+ |

> **Java version for the backend agent:** it compiles with **JDK 22** but fails with **JDK 27**, apparently because its Lombok library does not support that version. Versions between 22 and 27 have not been tested. Check with `mvn -version` or `java -version`.

> The agent and the load balancer **both use HTTP port 8080**, so run them on **different machines**. The dashboard expects the load balancer at `localhost:8080`, so run the dashboard on the same machine as the load balancer.

### Step 1: on every backend server, start LibreHardwareMonitor

1. Extract `HardwareMonitor.zip` and run LibreHardwareMonitor (as administrator, so that all sensors are readable).
2. Enable **Options → Remote Web Server**.
3. Check that `http://localhost:8085/data.json` opens in a browser.

### Step 2: on every backend server, start the agent

```bash
cd BackendServerAgent
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. If a newer JDK is the default, point `JAVA_HOME` to a JDK 17 to 22 installation first. On macOS, for example:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 22) sh mvnw spring-boot:run
```

To use a different sensor address, set `telemetry.librehardwaremonitor.url`.

### Step 3: start the load balancer

```bash
cd WebSocketForLoadBalancer
sh mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. Wait for `ONNX Model Loaded Successfully!!!`. Run it with Maven or an IDE; the model is read as a file from the build folder, so running the packaged JAR is not supported yet.

### Step 4: start the dashboard

```bash
cd "Load Balancer Frontend"
npm install
npm run dev
```

Open `http://localhost:5173`.

### Step 5: connect the servers

1. Put all machines on the same network, or install **Tailscale** on each.
2. In the dashboard, enter a backend's IP address and click **Connect**.
3. Wait about 40 seconds for the first prediction.

A server is monitored only while a dashboard page is connected to it. Changing pages closes the connections, so connect again on the new page.

---

## 🎬 Demonstration Guide

| Page | Address | What to show |
|---|---|---|
| Load Balancer | `/` | Live telemetry and connection events for each server |
| Prediction Demo | `/prediction-demo` | Apply a load to all servers and compare the forecast (yellow) with the measurement (green) |
| Server Health Demo | `/server-health-demo` | Give servers different loads and watch scores and queue order change |

Suggested flow:

1. Connect two or three servers and wait for predictions.
2. Show the live telemetry.
3. On the Prediction Demo, raise the load (for example from 20% to 90%) and show the forecast reacting. The **10 s forecast error** appears about 50 seconds after connecting.
4. On the Server Health Demo, load one server heavily and show it drop in the queue.
5. Stop one agent or LibreHardwareMonitor and show that the server leaves the queue with a warning.

---

## 🔌 API Reference (load balancer)

| Method | Endpoint | Purpose |
|---|---|---|
| WebSocket | `/lb-server-channel?backendIp=<ip>` | Telemetry stream with `predictedTemperature` and `thermalScore` added |
| GET | `/api/lb/queue` | Servers ordered from best to worst score |
| GET | `/api/lb/next-server` | Best server; returns 503 if the queue is empty |
| POST | `/proxy/stress/target-load?backendIp=<ip>` | Set CPU load on one server, body `{"targetLoad": 50}` |
| POST | `/proxy/stress/target-load/batch` | Body `{"backendIps": [...], "targetLoad": 50}` |

---

## ❓ Revision: Likely Panel Questions

**Why a CNN before the GRU?** The convolution picks up short local patterns, such as a sudden power spike, and the GRU follows how they develop over time.

**Why GRU over LSTM?** Their accuracy is almost identical, and the GRU does it with fewer parameters.

**Why ONNX?** The model is trained in Python but runs directly inside the Java load balancer, with no separate Python service.

**Why WebSockets?** Telemetry is a continuous stream; a persistent connection avoids a new HTTP request every 2 seconds.

**What happens if a sensor fails?** The server is removed from the queue, so that missing readings cannot make it look cool.

**How do you know the model is useful?** It beats the persistence baseline in every experiment phase.

---

## ⚠️ Current Limitations

- The system **ranks** servers but does not yet **forward requests** to them.
- Training and testing use different time blocks from the **same five laptops**; accuracy on an unseen laptop has not been established.
- The effect on temperature or energy compared with a conventional balancer (such as round robin) has not been measured.
- Thermal Score weights are hand-picked and combine different units.
- The live stress service loads the CPU only, while the training schedule also included GPU load.

## 🔮 Future Work

- Route real requests using the priority queue, and compare against round robin.
- Test on hardware that was not used in training.
- Tune the score weights from experiments.
- Longer prediction horizons.
- Restore a matching Transformer pipeline and compare it on identical data.

---

## 📜 License

Developed as part of a Computer Science capstone project for educational and research purposes.
