import { useMemo, useState, useEffect, useRef } from "react";
import useTelemetryTabs from "../hooks/useTelemetryTabs";

const POINTS = 30;
const usable = (value) => Number.isFinite(Number(value)) && Number(value) >= 0;
const temperature = (value) =>
  usable(value) ? `${Number(value).toFixed(1)}°C` : "—";
const scoreStr = (value) =>
  usable(value) ? Number(value).toFixed(2) : "—";
const hostForUrl = (host) =>
  host
    .trim()
    .replace(/^https?:\/\//, "")
    .replace(/\/$/, "");

function Stat({ label, value, highlight = false, color = "text-emerald-300" }) {
  return (
    <div className="rounded-xl bg-white/5 p-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 font-bold ${highlight ? color : ""}`}>
        {value}
      </p>
    </div>
  );
}

function TemperatureChartMini({ logs }) {
  const points = useMemo(
    () =>
      logs
        .slice(0, POINTS)
        .reverse()
        .map((log, i) => ({
          i,
          timestamp: log.timestamp,
          actual: Number(log.cpuTemperature),
          predicted: Number(log.predictedTemperature),
        }))
        .filter((point) => usable(point.actual)),
    [logs],
  );
  
  if (!points.length)
    return (
      <div className="h-32 flex items-center justify-center">
        <p className="text-xs text-slate-400">Waiting for data…</p>
      </div>
    );

  const values = points.flatMap((p) => [p.actual, p.predicted]).filter(usable);
  const min = Math.floor(Math.min(...values) - 2);
  const max = Math.ceil(Math.max(...values) + 2);
  const range = Math.max(max - min, 1),
    width = 300,
    height = 120,
    left = 25,
    right = 10,
    top = 10,
    bottom = 20;
  const x = (i) =>
    left + (i / Math.max(points.length - 1, 1)) * (width - left - right);
  const y = (v) => top + (1 - (v - min) / range) * (height - top - bottom);
  const line = (field) =>
    points
      .filter((p) => usable(p[field]))
      .map((p, i) => `${i ? "L" : "M"}${x(p.i)} ${y(p[field])}`)
      .join(" ");

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="w-full h-auto mt-4"
      role="img"
      aria-label="Temperature mini chart"
    >
      {[0, 1].map((ratio) => {
        const value = max - ratio * range,
          yy = y(value);
        return (
          <g key={ratio}>
            <line x1={left} x2={width - right} y1={yy} y2={yy} stroke="rgba(148,163,184,.15)" />
            <text x="0" y={yy + 4} fill="#94a3b8" fontSize="10">
              {value.toFixed(0)}°
            </text>
          </g>
        );
      })}
      <path d={line("actual")} fill="none" stroke="#4ade80" strokeWidth="2" strokeLinecap="round" />
      <path d={line("predicted")} fill="none" stroke="#facc15" strokeWidth="2" strokeLinecap="round" strokeDasharray="4" />
    </svg>
  );
}

function ServerCard({ tab, updateHost, updateName, connect, disconnect }) {
  const latest = tab.logs[0];
  const [targetLoad, setTargetLoad] = useState(50);
  const [message, setMessage] = useState("");
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
      const newEntry = { ip: host, name: tab.name.trim() };
      const filtered = currentHistory.filter(h => h.ip !== host);
      const newHistory = [newEntry, ...filtered].slice(0, 10);
      setHistory(newHistory);
      localStorage.setItem("lb_server_history", JSON.stringify(newHistory));
      window.dispatchEvent(new Event("lb_history_updated"));
    }
    connect();
  };

  const selectSuggestion = (sug) => {
    updateHost(sug.ip);
    if (sug.name) updateName(sug.name);
    setShowSuggestions(false);
  };

  async function applyLoad() {
    const load = Number(targetLoad);
    const host = hostForUrl(tab.backendHost);
    if (!Number.isInteger(load) || load < 0 || load > 100)
      return setMessage("Enter 0 to 100.");
    if (!host)
      return setMessage("Connect first.");
    try {
      const response = await fetch(`http://localhost:8080/proxy/stress/target-load?backendIp=${host}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ targetLoad: load }),
      });
      if (response.ok) {
        setMessage(`Target set to ${load}%`);
      } else {
        setMessage("Failed to reach server.");
      }
    } catch (err) {
      setMessage("Proxy error.");
    }
  }

  return (
    <article className="rounded-3xl border border-white/10 bg-slate-900/70 p-5 flex flex-col h-full">
      <div className="mb-4 flex items-center justify-between">
        <input
          value={tab.name}
          onChange={(e) => updateName(e.target.value)}
          placeholder="Server name"
          className="text-lg font-bold bg-transparent border-b border-transparent hover:border-slate-700 focus:border-amber-300 outline-none truncate w-full mr-3 transition-colors"
        />
        <span
          className={
            tab.connected
              ? "text-sm text-emerald-300 whitespace-nowrap"
              : "text-sm text-slate-400 whitespace-nowrap"
          }
        >
          {tab.connected ? "● Live" : "● Offline"}
        </span>
      </div>
      
      <div className="mb-4 flex gap-2 relative" ref={containerRef}>
        <input
          value={tab.backendHost}
          onChange={(e) => updateHost(e.target.value)}
          onFocus={() => setShowSuggestions(true)}
          placeholder="IP/Hostname"
          className="min-w-0 flex-1 rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm outline-none focus:border-amber-300 relative z-10"
        />
        <button
          onClick={tab.connected ? disconnect : handleConnect}
          className="rounded-xl bg-white/10 px-3 py-2 text-sm font-semibold hover:bg-white/20 relative z-10"
        >
          {tab.connected ? "Stop" : "Connect"}
        </button>

        {showSuggestions && history.length > 0 && (
          <div className="absolute top-[100%] left-0 mt-2 w-full bg-slate-800 border border-slate-700 rounded-xl shadow-xl z-50 max-h-60 overflow-y-auto">
            {history.map((sug, i) => (
              <div 
                key={i} 
                className="px-3 py-2 hover:bg-slate-700 cursor-pointer flex flex-col transition-colors border-b border-slate-700/50 last:border-0"
                onClick={() => selectSuggestion(sug)}
              >
                <div className="flex justify-between items-center gap-2">
                  <span className="font-medium text-white text-sm truncate">{sug.ip}</span>
                  {sug.name && <span className="text-xs text-amber-400 truncate">{sug.name}</span>}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-2 text-sm">
        <Stat label="CPU load" value={latest ? `${latest.cpuUsage}%` : "—"} />
        <Stat label="CPU temp" value={temperature(latest?.cpuTemperature)} />
        <Stat label="Predicted" value={temperature(latest?.predictedTemperature)} />
        <Stat label="Score" value={scoreStr(latest?.thermalScore)} highlight={usable(latest?.thermalScore)} color="text-amber-400" />
      </div>

      <div className="mt-4 mb-2 flex gap-2 items-center">
        <input
          type="number"
          min="0"
          max="100"
          value={targetLoad}
          onChange={(e) => setTargetLoad(e.target.value)}
          className="w-16 rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 text-sm outline-none focus:border-amber-300"
        />
        <button
          onClick={applyLoad}
          className="rounded-lg bg-slate-700 hover:bg-slate-600 px-3 py-1 text-xs font-bold text-white transition-colors"
        >
          Set Load
        </button>
        {message && <span className="text-[10px] text-slate-400">{message}</span>}
      </div>

      <div className="mt-auto">
        <TemperatureChartMini logs={tab.logs} />
      </div>
    </article>
  );
}

export default function ServerHealthDemo() {
  const { tabs, updateBackendHost, updateTabName, connect, disconnect } = useTelemetryTabs({
    initialServerCount: 3,
  });

  const [queueState, setQueueState] = useState([]);

  useEffect(() => {
    const fetchQueue = async () => {
      try {
        const response = await fetch("http://localhost:8080/api/lb/queue");
        if (response.ok) {
          const data = await response.json();
          setQueueState(data.queue || []);
        }
      } catch (e) {
        // silently ignore fetch errors to avoid console spam if backend is offline
      }
    };
    
    // Poll every 1 second
    const interval = setInterval(fetchQueue, 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(99,102,241,.15),transparent_35%),linear-gradient(180deg,#07111f,#111827)] p-6 text-white">
      <div className="mx-auto max-w-7xl">
        <a href="/" className="text-sm text-slate-300 hover:text-white">
          ← Telemetry dashboard
        </a>
        <h1 className="mt-5 text-4xl font-bold">Server Health Priority Queue</h1>
        <p className="mt-2 text-slate-300">
          Set individual loads on servers and watch how their Thermal Score reacts. 
          The Priority Queue at the bottom routes the next request to the server with the lowest score.
        </p>

        <section className="mt-6 grid gap-5 lg:grid-cols-3">
          {tabs.map((tab) => (
            <ServerCard
              key={tab.id}
              tab={tab}
              updateHost={(host) => updateBackendHost(tab.id, host)}
              updateName={(name) => updateTabName(tab.id, name)}
              connect={() => connect(tab.id, tab.backendHost, tab.name)}
              disconnect={() => disconnect(tab.id)}
            />
          ))}
        </section>

        <section className="mt-8 rounded-3xl border border-white/10 bg-slate-900/70 p-6 relative overflow-hidden">
          <div className="absolute top-0 right-0 p-3 bg-amber-500/10 rounded-bl-xl border-l border-b border-amber-500/20">
             <p className="text-xs text-amber-300 font-mono font-bold uppercase tracking-widest">Next Target</p>
          </div>
          
          <h2 className="mb-6 text-2xl font-bold flex items-center gap-3">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-indigo-400"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
            Live Priority Queue
          </h2>
          
          {queueState.length === 0 ? (
            <div className="py-8 text-center text-slate-500 italic border border-dashed border-white/10 rounded-xl bg-black/20">
              No servers connected or model warming up. Connect a server above to begin.
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              <div className="grid grid-cols-12 px-4 py-2 text-xs font-bold uppercase tracking-wider text-slate-400 bg-slate-950/50 rounded-lg">
                 <div className="col-span-1">Rank</div>
                 <div className="col-span-6">Server IP</div>
                 <div className="col-span-3 text-right">Score</div>
                 <div className="col-span-2 text-right">Status</div>
              </div>
              
              {queueState.map((server, index) => {
                const tabInfo = tabs.find(t => t.backendHost === server.backendIp);
                const name = tabInfo?.name || "Unknown";
                const isTop = index === 0;
                
                return (
                  <div 
                    key={server.backendIp} 
                    className={`grid grid-cols-12 items-center px-4 py-3 rounded-xl border transition-all ${
                      isTop 
                        ? "bg-indigo-900/30 border-indigo-500/50 shadow-[0_0_15px_rgba(99,102,241,0.2)]" 
                        : "bg-white/5 border-white/10"
                    }`}
                  >
                    <div className="col-span-1 font-mono font-bold text-lg">
                      <span className={isTop ? "text-indigo-400" : "text-slate-500"}>#{index + 1}</span>
                    </div>
                    <div className="col-span-6 flex flex-col">
                      <span className="font-bold">{name}</span>
                      <span className="text-xs text-slate-400 font-mono">{server.backendIp}</span>
                    </div>
                    <div className="col-span-3 text-right font-mono font-bold">
                      {server.thermalScore.toFixed(2)}
                    </div>
                    <div className="col-span-2 text-right">
                       {isTop && (
                         <span className="inline-block px-2 py-1 bg-indigo-500/20 text-indigo-300 text-[10px] font-bold rounded uppercase tracking-wider">
                           Active
                         </span>
                       )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
