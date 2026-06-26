import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const MAX_LOGS = 25;
const MAX_EVENTS = 30;

function createId() {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function createTab(name = "Server 1") {
  return {
    id: createId(),
    name,
    backendHost: "",
    connected: false,
    connecting: false,
    logs: [],
    events: [],
  };
}

export default function useTelemetryTabs() {
  const initialTab = useMemo(() => createTab(), []);
  const socketsRef = useRef(new Map());
  const [tabs, setTabs] = useState(() => [initialTab]);
  const [activeTabId, setActiveTabId] = useState(() => initialTab.id);

  const updateTab = useCallback((tabId, updater) => {
    setTabs((currentTabs) =>
      currentTabs.map((tab) =>
        tab.id === tabId ? { ...tab, ...updater(tab) } : tab,
      ),
    );
  }, []);

  const addEvent = useCallback(
    (tabId, type, message) => {
      updateTab(tabId, (tab) => ({
        events: [
          {
            id: createId(),
            timestamp: new Date().toISOString(),
            type,
            message,
          },
          ...tab.events,
        ].slice(0, MAX_EVENTS),
      }));
    },
    [updateTab],
  );

  const disconnect = useCallback(
    (tabId) => {
      const socket = socketsRef.current.get(tabId);

      if (socket) {
        socket.close();
        socketsRef.current.delete(tabId);
      }

      updateTab(tabId, () => ({
        connected: false,
        connecting: false,
      }));
    },
    [updateTab],
  );

  const connect = useCallback(
    (tabId, backendHost) => {
      const host = backendHost.trim();

      if (!host) {
        addEvent(tabId, "validation", "Please enter backend server IP");
        return;
      }

      const existingSocket = socketsRef.current.get(tabId);

      if (existingSocket) {
        existingSocket.close();
        socketsRef.current.delete(tabId);
      }

      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      const url = new URL(`${protocol}//localhost:8080/lb-server-channel`);
      url.searchParams.set("backendIp", host);

      const socket = new WebSocket(url.toString());
      socketsRef.current.set(tabId, socket);

      updateTab(tabId, () => ({
        backendHost: host,
        connecting: true,
        connected: false,
      }));

      socket.onopen = () => {
        updateTab(tabId, () => ({
          name: host,
          backendHost: host,
          connected: true,
          connecting: false,
        }));
        addEvent(tabId, "connection", `Connected to ${host}`);
      };

      socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);

          if (payload.type) {
            addEvent(tabId, payload.type, payload.message || JSON.stringify(payload));
            return;
          }

          updateTab(tabId, (tab) => ({
            logs: [
              {
                id: createId(),
                ...payload,
              },
              ...tab.logs,
            ].slice(0, MAX_LOGS),
          }));
        } catch {
          addEvent(tabId, "error", "Failed to parse payload");
        }
      };

      socket.onerror = () => {
        addEvent(tabId, "error", `WebSocket error for ${host}`);
      };

      socket.onclose = () => {
        socketsRef.current.delete(tabId);
        updateTab(tabId, () => ({
          connected: false,
          connecting: false,
        }));
        addEvent(tabId, "connection", `Disconnected from ${host}`);
      };
    },
    [addEvent, updateTab],
  );

  const addTab = useCallback(() => {
    setTabs((currentTabs) => {
      const nextTab = createTab(`Server ${currentTabs.length + 1}`);
      setActiveTabId(nextTab.id);

      return [...currentTabs, nextTab];
    });
  }, []);

  const closeTab = useCallback(
    (tabId) => {
      disconnect(tabId);

      setTabs((currentTabs) => {
        if (currentTabs.length === 1) {
          return currentTabs;
        }

        const tabIndex = currentTabs.findIndex((tab) => tab.id === tabId);
        const nextTabs = currentTabs.filter((tab) => tab.id !== tabId);

        if (activeTabId === tabId) {
          const nextActiveTab =
            nextTabs[Math.max(0, tabIndex - 1)] || nextTabs[0];
          setActiveTabId(nextActiveTab.id);
        }

        return nextTabs;
      });
    },
    [activeTabId, disconnect],
  );

  const updateBackendHost = useCallback(
    (tabId, backendHost) => {
      updateTab(tabId, () => ({ backendHost }));
    },
    [updateTab],
  );

  useEffect(() => {
    const sockets = socketsRef.current;

    return () => {
      sockets.forEach((socket) => socket.close());
      sockets.clear();
    };
  }, []);

  const activeTab = tabs.find((tab) => tab.id === activeTabId) || tabs[0];

  return {
    tabs,
    activeTab,
    activeTabId: activeTab.id,
    setActiveTabId,
    addTab,
    closeTab,
    updateBackendHost,
    connect,
    disconnect,
  };
}
