# 🌡️ Thermal-Aware Load Balancer

A distributed **Thermal-Aware Load Balancer** that demonstrates how intelligent request scheduling based on real-time hardware telemetry can reduce server temperatures and improve energy efficiency.

The project collects live system telemetry from backend servers, relays it through a centralized WebSocket-based load balancer, visualizes the data in a modern React dashboard, and prepares datasets for LSTM-based thermal prediction.

---

# ✨ Features

- 🌡️ Real-time CPU, GPU, RAM, and network telemetry
- 🔌 Distributed architecture using WebSockets
- 🌍 Remote monitoring across multiple systems using Tailscale
- 📊 Modern React dashboard built with Vite
- 📑 Live telemetry streaming with JSON logging
- 🔄 Multiple backend server support
- 📡 Connection event monitoring
- 🤖 LSTM-ready telemetry dataset generation
- 🌬️ Foundation for thermal-aware request scheduling
- ⚡ Demonstrates energy-efficient load balancing concepts

---

# 🏗️ Architecture

```text
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
Backend Server 1      Backend Server 2     Backend Server N
Telemetry Server      Telemetry Server     Telemetry Server
(WebSocket :8086)     (WebSocket :8086)    (WebSocket :8086)
          │                   │                   │
          ▼                   ▼                   ▼
 ThermalTelemetryCollector (collects hardware metrics)
          │
          ▼
 LibreHardwareMonitor
```

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
- Jupyter Notebook
- LSTM (TensorFlow/Keras)

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
├── ThermalTelemetryCollector/
│   ├── Telemetry Collector
│   ├── Hardware Monitor Client
│   └── WebSocket Server
│
├── Stress Generator/
│   └── Generates CPU stress events
│
├── LSTM/
│   ├── after_stress_generator.ipynb
│   └── cross_hardware_lstm.ipynb
│
└── README.md
```

---

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

4. The Spring Boot Load Balancer receives telemetry from all connected backend servers.

5. The React dashboard displays:

- Live telemetry
- Connection events
- Historical telemetry
- Multiple backend servers simultaneously

6. The collected telemetry is later processed to generate datasets for LSTM-based temperature prediction.

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

## Step 3 — Run StressTestRunner

Navigate to the **Stress Generator** project and execute:

```
StressTestRunner
```

The stress generator creates controlled CPU load while generating corresponding event logs.

---

## Step 4 — Merge Telemetry and Event Logs

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

## Step 5 — Train the LSTM Model

After the merged dataset has been generated, run:

```
cross_hardware_lstm.ipynb
```

This notebook:

- Loads the merged telemetry dataset
- Performs preprocessing and feature engineering
- Trains an LSTM model for CPU temperature prediction
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

# 🤖 Machine Learning Pipeline

The project also provides a complete workflow for thermal prediction.

```
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
cross_hardware_lstm.ipynb
        │
        ▼
LSTM Temperature Prediction Model
```

---

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
- Enable predictive thermal management using LSTM models

---

# 📜 License

Developed as part of a Computer Science Capstone Project for educational and research purposes.