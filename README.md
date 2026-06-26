🌡️ Thermal-Aware Load Balancer

A distributed Thermal-Aware Load Balancer that demonstrates how intelligent request scheduling based on real-time hardware telemetry can reduce server temperatures and improve energy efficiency.

The project collects live system telemetry from multiple backend servers, relays it through a centralized WebSocket-based load balancer, and visualizes the data in a modern React dashboard. Multiple backend servers can be monitored simultaneously over a Tailscale network.

⸻

 Features

* 🌡️ Real-time CPU, GPU, RAM and network telemetry
* 🔌 Distributed architecture using WebSockets
* 🌍 Remote monitoring across cities using Tailscale
* 📊 Modern React + Tailwind dashboard
* 📑 Live telemetry stream with historical logs
* 🔄 Multiple backend server connections
* 📡 Connection event monitoring
* 🎛️ Foundation for intelligent request routing
* 🌬️ Future support for dynamic fan speed regulation
* ⚡ Designed to demonstrate energy-efficient load balancing

⸻

## 🏗️ Architecture

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

⸻
```
Technology Stack

Frontend

* React
* Tailwind CSS
* Vite

Backend

* Spring Boot
* Java WebSocket
* Java HttpClient WebSocket

Monitoring

* LibreHardwareMonitor
* JSON Logging

Networking

* WebSockets
* Tailscale VPN

⸻

📂 Project Structure

ThermalAwareLoadBalancer/

├── Load Balancer Frontend/
│   ├── React
│   ├── Tailwind
│   └── Dashboard
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
└── README.md

⸻

⚙️ How It Works

1. Every backend server continuously monitors its hardware statistics.
2. The ThermalTelemetryCollector gathers metrics such as:

* CPU Usage
* CPU Temperature
* GPU Temperature
* GPU Memory Usage
* RAM Usage
* Network Connections
* Running Processes

3. Every 2 seconds, the telemetry collector:

* Collects the latest hardware statistics
* Saves them to JSON logs
* Broadcasts them through a WebSocket server

4. The Spring Boot Load Balancer acts as a WebSocket client for each backend server.
5. The Load Balancer forwards telemetry to the React dashboard.
6. The dashboard displays:

* Live telemetry
* Connection events
* Telemetry history
* Multiple backend servers simultaneously

⸻

▶️ Running the Project

Step 1 — Start the Load Balancer

Run the Spring Boot project:

WebSocketForLoadBalancer

Default Port:

8080

⸻

Step 2 — Start the Frontend

Navigate to:

Load Balancer Frontend

Install dependencies:

npm install

Run:

npm run dev

Default URL:

http://localhost:5173

⸻

Step 3 — Prepare a Backend Server

On each backend server:

1. Start LibreHardwareMonitor

Enable:

Options
    → Remote Web Server

2. Run

ThermalTelemetryCollector

The collector connects to LibreHardwareMonitor, gathers telemetry every two seconds, and exposes a WebSocket server on:

ws://<server-ip>:8086/telemetry

⸻

Step 4 — Connect Through Tailscale

Copy the backend server’s Tailscale IP address.

Inside the React dashboard:

* Create a server tab
* Enter the backend server’s Tailscale IP
* Connect

The Spring Boot Load Balancer automatically establishes a WebSocket connection to that backend and begins streaming telemetry.

⸻

📊 Live Dashboard

The dashboard currently supports:

* Live telemetry cards
* Connection status
* Connection events
* Telemetry history
* Multiple server tabs
* Remote server monitoring

⸻

🔮 Future Enhancements

* 🤖 AI-based fan speed prediction
* 🧠 Temperature-aware request scheduling
* ⚖️ Dynamic load balancing algorithm
* 📈 Historical charts and analytics
* 🔔 Thermal alerts
* 🧮 Power consumption estimation
* 🌬️ Remote fan speed regulation
* 📊 Server comparison dashboard
* ☁️ Cloud deployment

⸻

Project Goal

Traditional load balancers distribute requests primarily based on server load. This project explores a different approach by incorporating real-time thermal telemetry into routing decisions.

The long-term objective is to demonstrate that intelligent thermal-aware scheduling can:

* Reduce server temperatures
* Lower cooling requirements
* Improve energy efficiency
* Extend hardware lifespan
* Optimize overall data center performance

⸻

📜 License

This project was developed as part of a Computer Science capstone project for educational and research purposes.
