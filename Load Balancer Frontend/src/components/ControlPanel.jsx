import { useState, useEffect, useRef } from "react";

export default function ControlPanel({
  tab,
  connect,
  disconnect,
  updateBackendHost,
}) {
  const [serverName, setServerName] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [history, setHistory] = useState([]);
  const containerRef = useRef(null);

  useEffect(() => {
    const loadHistory = () => {
      try {
        const stored = localStorage.getItem("lb_server_history");
        if (stored) setHistory(JSON.parse(stored));
      } catch (e) {
        console.error(e);
      }
    };

    loadHistory();
    window.addEventListener("lb_history_updated", loadHistory);
    return () => window.removeEventListener("lb_history_updated", loadHistory);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleConnect = () => {
    const host = tab.backendHost.trim();
    if (host) {
      let currentHistory = [];
      try {
        const stored = localStorage.getItem("lb_server_history");
        if (stored) currentHistory = JSON.parse(stored);
      } catch(e) {}

      const newEntry = { ip: host, name: serverName.trim() };
      const filtered = currentHistory.filter(h => h.ip !== host);
      const newHistory = [newEntry, ...filtered].slice(0, 10);
      setHistory(newHistory);
      localStorage.setItem("lb_server_history", JSON.stringify(newHistory));
      window.dispatchEvent(new Event("lb_history_updated"));
    }
    connect(tab.backendHost, serverName.trim());
  };

  const selectSuggestion = (sug) => {
    updateBackendHost(sug.ip);
    setServerName(sug.name || "");
    setShowSuggestions(false);
  };

  return (
    <section
      className="
      mt-5
      rounded-3xl
      border border-white/10
      bg-slate-900/70
      p-6
    "
    >
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <label className="text-slate-300">Backend Server Setup</label>

        <span
          className={`
            inline-flex
            items-center
            gap-2
            rounded-full
            px-3
            py-1.5
            text-sm
            font-semibold
            ${
              tab.connected
                ? "bg-green-500/10 text-green-300"
                : tab.connecting
                  ? "bg-amber-500/10 text-amber-300"
                  : "bg-red-500/10 text-red-300"
            }
          `}
        >
          <span className="h-2.5 w-2.5 rounded-full bg-current" />
          {tab.connected ? "Connected" : tab.connecting ? "Connecting" : "Disconnected"}
        </span>
      </div>

      <div className="flex flex-col gap-4 relative" ref={containerRef}>
        <div className="flex flex-wrap gap-3 items-end">
          <div className="flex flex-col gap-2 flex-1 min-w-[280px] max-w-[400px]">
            <label className="text-slate-400 text-sm font-medium">Server Name (Optional)</label>
            <input
              type="text"
              value={serverName}
              onChange={(e) => setServerName(e.target.value)}
              onFocus={() => setShowSuggestions(true)}
              placeholder="e.g. Primary Backend"
              className="
                w-full
                rounded-2xl
                border border-slate-700
                bg-slate-950
                px-4
                py-3
                outline-none
                focus:border-blue-400
                transition-colors
              "
            />
          </div>
        </div>

        <div className="flex flex-wrap gap-3 items-end">
          <div className="flex flex-col gap-2 flex-1 min-w-[280px]">
            <label className="text-slate-400 text-sm font-medium">IP Address</label>
            <input
              type="text"
              value={tab.backendHost}
              onChange={(e) => updateBackendHost(e.target.value)}
              onFocus={() => setShowSuggestions(true)}
              placeholder="192.168.1.50"
              className="
                w-full
                rounded-2xl
                border border-slate-700
                bg-slate-950
                px-4
                py-4
                outline-none
                focus:border-blue-400
                transition-colors
              "
            />
          </div>

          <button
            onClick={handleConnect}
            disabled={tab.connecting}
            className="
            rounded-2xl
            px-6
            py-4
            h-[58px]
            font-bold
            bg-gradient-to-r
            from-amber-500
            to-pink-500
            disabled:opacity-50
            transition-opacity
            hover:opacity-90
          "
          >
            {tab.connecting ? "Connecting..." : "Connect"}
          </button>

          <button
            disabled={!tab.connected && !tab.connecting}
            onClick={disconnect}
            className="
            rounded-2xl
            px-6
            py-4
            h-[58px]
            font-bold
            bg-white/10
            disabled:opacity-40
            transition-colors
            hover:bg-white/20
          "
          >
            Disconnect
          </button>
        </div>

        {showSuggestions && history.length > 0 && (
          <div className="absolute top-[100%] left-0 mt-2 w-full max-w-[400px] bg-slate-800 border border-slate-700 rounded-xl shadow-xl z-20 max-h-60 overflow-y-auto">
            <div className="px-4 py-3 text-xs font-bold text-slate-400 uppercase tracking-wider bg-slate-800/90 backdrop-blur sticky top-0 border-b border-slate-700/50">
              Recent Servers
            </div>
            {history.map((sug, i) => (
              <div 
                key={i} 
                className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex flex-col transition-colors border-b border-slate-700/50 last:border-0"
                onClick={() => selectSuggestion(sug)}
              >
                <div className="flex justify-between items-center gap-2">
                  <span className="font-medium text-white truncate">{sug.ip}</span>
                  {sug.name && <span className="text-sm text-amber-400 truncate">{sug.name}</span>}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
