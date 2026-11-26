// THIS IS THE FRONTEND LOGIC
let socket = null;

function byId(id) {
  return document.getElementById(id);
}

function setConnected(connected) {
  byId("connect").disabled = connected;
}
  (() => {
    const { useEffect, useMemo, useRef, useState } = React;

    function formatValue(value, suffix = "") {
      if (value === null || value === undefined || value === "") {
        return "-";
      }

      return `${value}${suffix}`;
    }

    function App() {
      const socketRef = useRef(null);
      const [backendIp, setBackendIp] = useState("");
      const [status, setStatus] = useState("Disconnected");
      const [connected, setConnected] = useState(false);
      const [logs, setLogs] = useState([]);
      const [events, setEvents] = useState([]);

      const createId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;

      const pushEvent = (type, message) => {
        setEvents((current) => [
          { id: createId(), timestamp: new Date().toISOString(), type, message },
          ...current,
        ].slice(0, 30));
      };

      const pushLog = (payload) => {
        setLogs((current) => [
          { id: createId(), ...payload },
          ...current,
        ].slice(0, 25));
      };

      const disconnect = () => {
        if (socketRef.current) {
          socketRef.current.close();
          socketRef.current = null;
        }
      };

      const connect = () => {
        const trimmedIp = backendIp.trim();
        if (!trimmedIp) {
          pushEvent("validation", "Enter a backend IP address first.");
          return;
        }

        disconnect();

        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const url = new URL(`${protocol}//${window.location.host}/lb-server-channel`);
        url.searchParams.set("backendIp", trimmedIp);

        setStatus("Connecting");
        const socket = new WebSocket(url.toString());
        socketRef.current = socket;

        socket.onopen = () => {
          setConnected(true);
          setStatus("Connected");
          pushEvent("connection", `Connected to load balancer relay for ${trimmedIp}`);
        };

        socket.onmessage = (event) => {
          try {
            const payload = JSON.parse(event.data);
            if (payload.type === "connectionEstablished") {
              pushEvent("connection", `Relay established for backend ${payload.backendIp}:${payload.backendPort}`);
              return;
            }

            if (payload.type === "backendConnected") {
              pushEvent("backend", payload.message || "Backend connected");
              return;
            }

            if (payload.type === "backendDisconnected" || payload.type === "error") {
              pushEvent(payload.type, payload.message || payload.type);
              return;
            }

            if (payload.type) {
              pushEvent(payload.type, JSON.stringify(payload));
              return;
            }

            pushLog(payload);
          }
          catch (error) {
            pushEvent("parseError", `Failed to parse payload: ${event.data}`);
          }
        };

        socket.onclose = () => {
          setConnected(false);
          setStatus("Disconnected");
          pushEvent("connection", "WebSocket session closed");
          socketRef.current = null;
        };

        socket.onerror = () => {
          pushEvent("error", "WebSocket transport error");
        };
      };

      useEffect(() => () => disconnect(), []);

      const latestLog = useMemo(() => logs[0] || null, [logs]);

      return React.createElement(
        "div",
        { className: "shell" },
        React.createElement(
          "header",
          { className: "hero" },
          React.createElement("div", { className: "eyebrow" }, "Load Balancer Frontend"),
          React.createElement("h1", null, "Backend telemetry streamed through the relay"),
          React.createElement(
            "p",
            { className: "hero-copy" },
            "Type the backend server IP, connect, and the load balancer will open a WebSocket to the collector running on that machine. The collector keeps writing system_logs.jsonl and pushes a new snapshot every 2 seconds."
          ),
          React.createElement(
            "div",
            { className: `status-pill ${connected ? "status-on" : "status-off"}` },
            status
          )
        ),
        React.createElement(
          "section",
          { className: "panel controls" },
          React.createElement(
            "label",
            { htmlFor: "backendIp" },
            "Backend server IP"
          ),
          React.createElement(
            "div",
            { className: "input-row" },
            React.createElement("input", {
              id: "backendIp",
              type: "text",
              value: backendIp,
              onChange: (event) => setBackendIp(event.target.value),
              placeholder: "192.168.1.50",
              autoComplete: "off",
            }),
            React.createElement(
              "button",
              { className: "primary", onClick: connect, type: "button" },
              "Connect"
            ),
            React.createElement(
              "button",
              { className: "secondary", onClick: disconnect, type: "button", disabled: !connected },
              "Disconnect"
            )
          )
        ),
        React.createElement(
          "section",
          { className: "grid" },
          React.createElement(
            "div",
            { className: "panel" },
            React.createElement("h2", null, "Live telemetry"),
            React.createElement(
              "div",
              { className: "stats-card" },
              latestLog
                ? React.createElement(
                    React.Fragment,
                    null,
                    React.createElement("div", null, React.createElement("span", null, "Timestamp"), React.createElement("strong", null, formatValue(latestLog.timestamp))),
                    React.createElement("div", null, React.createElement("span", null, "CPU usage"), React.createElement("strong", null, formatValue(latestLog.cpuUsage, "%"))),
                    React.createElement("div", null, React.createElement("span", null, "CPU temperature"), React.createElement("strong", null, formatValue(latestLog.cpuTemperature, "°C"))),
                    React.createElement("div", null, React.createElement("span", null, "GPU temperature"), React.createElement("strong", null, formatValue(latestLog.gpuTemperature, "°C"))),
                    React.createElement("div", null, React.createElement("span", null, "GPU memory load"), React.createElement("strong", null, formatValue(latestLog.gpuMemoryLoad, "%"))),
                    React.createElement("div", null, React.createElement("span", null, "RAM usage"), React.createElement("strong", null, formatValue(latestLog.ramUsage, "%"))),
                    React.createElement("div", null, React.createElement("span", null, "Network connections"), React.createElement("strong", null, formatValue(latestLog.networkConnections))),
                    React.createElement("div", null, React.createElement("span", null, "Process count"), React.createElement("strong", null, formatValue(latestLog.processCount)))
                  )
                : React.createElement("p", { className: "empty-state" }, "No telemetry received yet.")
            )
          ),
          React.createElement(
            "div",
            { className: "panel" },
            React.createElement("h2", null, "Connection events"),
            React.createElement(
              "div",
              { className: "table-wrap" },
              React.createElement(
                "table",
                null,
                React.createElement(
                  "thead",
                  null,
                  React.createElement(
                    "tr",
                    null,
                    React.createElement("th", null, "Timestamp"),
                    React.createElement("th", null, "Type"),
                    React.createElement("th", null, "Message")
                  )
                ),
                React.createElement(
                  "tbody",
                  null,
                  events.map((entry) =>
                    React.createElement(
                      "tr",
                      { key: entry.id },
                      React.createElement("td", null, entry.timestamp),
                      React.createElement("td", null, entry.type),
                      React.createElement("td", null, entry.message)
                    )
                  )
                )
              )
            )
          )
        ),
        React.createElement(
          "section",
          { className: "panel" },
          React.createElement("h2", null, "Telemetry stream"),
          React.createElement(
            "div",
            { className: "table-wrap" },
            React.createElement(
              "table",
              null,
              React.createElement(
                "thead",
                null,
                React.createElement(
                  "tr",
                  null,
                  React.createElement("th", null, "Timestamp"),
                  React.createElement("th", null, "CPU"),
                  React.createElement("th", null, "CPU Temp"),
                  React.createElement("th", null, "GPU Temp"),
                  React.createElement("th", null, "GPU Mem"),
                  React.createElement("th", null, "RAM"),
                  React.createElement("th", null, "Net"),
                  React.createElement("th", null, "Processes")
                )
              ),
              React.createElement(
                "tbody",
                null,
                logs.map((entry) =>
                  React.createElement(
                    "tr",
                    { key: entry.id },
                    React.createElement("td", null, entry.timestamp),
                    React.createElement("td", null, formatValue(entry.cpuUsage, "%")),
                    React.createElement("td", null, formatValue(entry.cpuTemperature, "°C")),
                    React.createElement("td", null, formatValue(entry.gpuTemperature, "°C")),
                    React.createElement("td", null, formatValue(entry.gpuMemoryLoad, "%")),
                    React.createElement("td", null, formatValue(entry.ramUsage, "%")),
                    React.createElement("td", null, formatValue(entry.networkConnections)),
                    React.createElement("td", null, formatValue(entry.processCount))
                  )
                )
              )
            )
          )
        )
      );
    }

    ReactDOM.createRoot(document.getElementById("root")).render(
      React.createElement(App)
    );
  })();
