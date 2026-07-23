# 🌡️ Thermal-Aware Load Balancer

A distributed **Thermal-Aware Load Balancer** that demonstrates how intelligent request scheduling based on real-time hardware telemetry can reduce server temperatures and improve energy efficiency.

The project collects live system telemetry from backend servers, relays it through a centralized WebSocket-based load balancer, visualizes the data in a modern React dashboard, and prepares datasets for LSTM-based thermal prediction.

---

# ✨ Features

- 🌡️ Real-time CPU, GPU, memory, storage, and network telemetry collection
- 🔌 Distributed architecture using Spring Boot and WebSockets
- 🌍 Remote monitoring across multiple backend systems using Tailscale
- 📊 Modern React dashboard built with Vite
- 📑 Live telemetry streaming with JSON logging
- 🔄 Multiple backend server support through BackendServerAgent
- 📡 Connection and server event monitoring
- 🤖 Dataset generation for deep learning-based temperature prediction
- 🧠 Support for CNN + GRU and CNN + LSTM temperature prediction models
- 📦 ONNX model deployment for real-time inference
- 🌬️ Foundation for thermal-aware request scheduling
- ⚡ Demonstrates energy-efficient load balancing concepts

# 🏗️ Architecture

                         React Frontend
                    (Dashboard - Port 5173)
                              │
                              │ WebSocket
                              ▼
                  Spring Boot Load Balancer
                  (Relay Server - Port 8080)
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
 BackendServerAgent   BackendServerAgent   BackendServerAgent
      Server 1             Server 2             Server N
          │                   │                   │
          ▼                   ▼                   ▼
 ThermalTelemetryCollector (Hardware Telemetry)
          │
          ▼
 LibreHardwareMonitor

---

# 🛠 Technology Stack

## Frontend

- React
- Vite

## Backend

- Spring Boot
- Java WebSocket
- Java HttpClient WebSocket

## Monitoring

- LibreHardwareMonitor
- JSON Logging

## Machine Learning

- Python
- TensorFlow / Keras
- CNN + GRU
- CNN + LSTM
- ONNX Runtime
- Jupyter Notebook

## Networking

- WebSockets
- Tailscale VPN

---

# 📂 Project Structure

```text
ThermalAwareLoadBalancer/

├── Load Balancer Frontend/
│   ├── React Dashboard
│   └── Vite
│
├── WebSocketForLoadBalancer/
│   ├── Spring Boot
│   ├── WebSocket Relay
│   └── Request Router
│
├── BackendServerAgent/
│   ├── Spring Boot Application
│   ├── Backend Server Agent
│   └── WebSocket Client
│
├── ThermalTelemetryCollector/
│   ├── Telemetry Collector
│   ├── Hardware Monitor Client
│   └── WebSocket Server
│
├── Stress Generator/
│   └── Generates CPU stress events
│
├── cpu-temperature-lstm-v2/
│   ├── Dataset Generation
│   ├── Model Training
│   ├── ONNX Export
│   └── Model Evaluation
│
└── README.md
```

# ⚙️ How It Works

1. Every backend server continuously monitors its hardware statistics.
2. The **ThermalTelemetryCollector** gathers:

- CPU Usage
- CPU Temperature
- GPU Usage
- GPU Temperature
- RAM Usage
- Network Connections
- Running Processes

3. Every **2 seconds**, the collector:

- Reads hardware telemetry from LibreHardwareMonitor
- Stores telemetry in JSON logs
- Broadcasts telemetry over WebSockets

4. Each backend machine runs the **BackendServerAgent**, which connects to the centralized Load Balancer.

5. The BackendServerAgent receives telemetry from the ThermalTelemetryCollector and forwards it to the Spring Boot Load Balancer over WebSockets.

6. The Load Balancer relays telemetry to the React dashboard for live monitoring and also performs real-time inference using the exported ONNX model for CPU temperature prediction.

---

# ▶️ Running the Project

## Step 1 — Start LibreHardwareMonitor

Open **LibreHardwareMonitor**.

Enable:

```
Options
    → Remote Web Server
```

This exposes the hardware telemetry required by the telemetry collector.

---

## Step 2 — Run ThermalTelemetryCollector

Start the **ThermalTelemetryCollector** application.

The collector:

- Reads hardware telemetry
- Stores telemetry in `system_logs.jsonl`
- Starts a WebSocket server

Default endpoint:

```
ws://<server-ip>:8086/telemetry
```

---
---

## Step 3 — Run BackendServerAgent

Navigate to the BackendServerAgent project.

Open a terminal inside the project folder and run:

```bash
./mvnw spring-boot:run
```

Alternatively, run:

```
BackendServerAgent
└── src
    └── main
        └── java
            └── BackendServerAgentApplication.java
```

This service connects the backend machine to the centralized Load Balancer and continuously forwards telemetry collected by the ThermalTelemetryCollector.

---

## Step 4 — Run StressTestRunner

Navigate to the **Stress Generator** project and execute:

```
StressTestRunner
```

The stress generator creates controlled CPU load while generating corresponding event logs.

---

## Step 5 — Merge Telemetry and Event Logs

Navigate to the **LSTM** folder.

Run:

```
after_stress_generator.ipynb
```

This notebook:

- Reads `system_logs.jsonl`
- Reads the generated event logs
- Synchronizes timestamps
- Merges both datasets into a single training dataset

---

## Step 6 — Train the Prediction Model

After the merged dataset has been generated, run:

```
cross_hardware_lstm.ipynb
```

This notebook:

- Loads the merged telemetry dataset
- Performs preprocessing and feature engineering
- Trains the latest deep learning model (CNN + GRU / CNN + LSTM / Transformer, depending on the experiment) and exports the best-performing model for deployment.
- Evaluates prediction accuracy across hardware configurations

---

# 🌍 Running the Distributed Dashboard

## Start the Load Balancer

Run:

```
WebSocketForLoadBalancer
```

Default Port:

```
8080
```

---

## Start the Frontend

Navigate to:

```
Load Balancer Frontend
```

Install dependencies:

```bash
npm install
```

Run:

```bash
npm run dev
```

Frontend:

```
http://localhost:5173
```

---

## Connect Remote Servers

1. Install and configure **Tailscale** on all backend systems.
2. Copy the backend server's Tailscale IP.
3. Open the dashboard.
4. Create a new server tab.
5. Enter the Tailscale IP.
6. Connect.

The Load Balancer automatically establishes a WebSocket connection and begins streaming telemetry.

---

# 📊 Dashboard Features

- Live telemetry cards
- CPU & GPU monitoring
- Connection status
- Connection event logs
- Historical telemetry
- Multiple backend monitoring

---

LibreHardwareMonitor
        │
        ▼
ThermalTelemetryCollector
        │
        ▼
system_logs.jsonl
        │
        ▼
StressTestRunner
        │
        ▼
events.json
        │
        ▼
after_stress_generator.ipynb
        │
        ▼
Merged Dataset
        │
        ▼
Model Training
(CNN + GRU / CNN + LSTM / Transformer)
        │
        ▼
Best Model Selection
        │
        ▼
ONNX Export
        │
        ▼
BackendServerAgent
        │
        ▼
Thermal-Aware Load Balancer

# 🔮 Future Enhancements

- AI-based fan speed prediction
- Thermal-aware request scheduling
- Intelligent load balancing algorithms
- Historical analytics dashboard
- Thermal alerts
- Power consumption estimation
- Remote fan speed regulation
- Server comparison dashboard
- Cloud deployment

---

# 🎯 Project Goal

Traditional load balancers distribute requests primarily based on CPU utilization or request count.

This project explores a **thermal-aware approach**, where real-time hardware telemetry is used to make smarter routing decisions and generate predictive models for future thermal behavior.

The long-term objectives are to:

- Reduce server temperatures
- Improve energy efficiency
- Lower cooling requirements
- Extend hardware lifespan
- Enable predictive thermal management using deep learning models deployed through ONNX for real-time inference.

---

# 📜 License

Developed as part of a Computer Science Capstone Project for educational and research purposes.