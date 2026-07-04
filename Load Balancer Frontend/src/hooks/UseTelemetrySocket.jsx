import { useRef, useState } from "react";

export default function useTelemetrySocket() {
  const socketRef = useRef(null);

  const [connected, setConnected] = useState(false);
  const [logs, setLogs] = useState([]);
  const [events, setEvents] = useState([]);

  const createId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  const addEvent = (type, message) => {
    setEvents((prev) =>
      [
        {
          id: createId(),
          timestamp: new Date().toISOString(),
          type,
          message,
        },
        ...prev,
      ].slice(0, 30),
    );
  };

  const connect = (backendIp) => {
    if (!backendIp.trim()) {
      addEvent("validation", "Please enter backend IP");
      return;
    }

    disconnect();

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";

    const url = new URL(`${protocol}//localhost:8080/lb-server-channel`);

    url.searchParams.set("backendIp", backendIp);
    console.log(url.toString());
    const socket = new WebSocket(url.toString());

    socketRef.current = socket;

    socket.onopen = () => {
      setConnected(true);
      addEvent("connection", `Connected to ${backendIp}`);
    };

    socket.onmessage = (e) => {
      try {
        const payload = JSON.parse(e.data);

        if (payload.type) {
          addEvent(payload.type, payload.message || JSON.stringify(payload));
          return;
        }

        setLogs((prev) =>
          [
            {
              id: createId(),
              ...payload,
            },
            ...prev,
          ].slice(0, 25),
        );
      } catch {
        addEvent("error", "Failed to parse payload");
      }
    };

    socket.onclose = () => {
      setConnected(false);
      addEvent("connection", "Disconnected");
    };
  };

  const disconnect = () => {
    if (socketRef.current) {
      socketRef.current.close();

      socketRef.current = null;
    }
  };

  return {
    connected,
    logs,
    events,
    connect,
    disconnect,
  };
}
