import { useMemo, useState } from "react";
import useTelemetryTabs from "../hooks/useTelemetryTabs";

const POINTS = 30;
const usable = (value) => Number.isFinite(Number(value)) && Number(value) >= 0;
const temperature = (value) => (usable(value) ? `${Number(value).toFixed(1)}°C` : "—");
const hostForUrl = (host) => host.trim().replace(/^https?:\/\//, "").replace(/\/$/, "");

function Stat({ label, value, green = false }) {
  return <div className="rounded-xl bg-white/5 p-3"><p className="text-xs text-slate-400">{label}</p><p className={`mt-1 font-bold ${green ? "text-emerald-300" : ""}`}>{value}</p></div>;
}

function TemperatureChart({ logs }) {
  const points = useMemo(() => logs.slice(0, POINTS).reverse().map((log, i) => ({ i, timestamp: log.timestamp, actual: Number(log.cpuTemperature), predicted: Number(log.predictedTemperature) })).filter((point) => usable(point.actual)), [logs]);
  if (!points.length) return <p className="py-12 text-center text-sm text-slate-400">Waiting for telemetry data…</p>;

  const values = points.flatMap((p) => [p.actual, p.predicted]).filter(usable);
  const min = Math.floor(Math.min(...values) - 2);
  const max = Math.ceil(Math.max(...values) + 2);
  const range = Math.max(max - min, 1), width = 640, height = 260, left = 46, right = 18, top = 18, bottom = 38;
  const x = (i) => left + (i / Math.max(points.length - 1, 1)) * (width - left - right);
  const y = (v) => top + (1 - (v - min) / range) * (height - top - bottom);
  const line = (field) => points.filter((p) => usable(p[field])).map((p, i) => `${i ? "L" : "M"}${x(p.i)} ${y(p[field])}`).join(" ");

  return <div className="overflow-x-auto"><svg viewBox={`0 0 ${width} ${height}`} className="min-w-[560px] w-full" role="img" aria-label="Temperature versus time chart">
    {[0, 0.5, 1].map((ratio) => { const value = max - ratio * range, yy = y(value); return <g key={ratio}><line x1={left} x2={width - right} y1={yy} y2={yy} stroke="rgba(148,163,184,.25)" /><text x="4" y={yy + 4} fill="#94a3b8" fontSize="11">{value.toFixed(0)}°</text></g>; })}
    <path d={line("actual")} fill="none" stroke="#4ade80" strokeWidth="3" strokeLinecap="round" />
    <path d={line("predicted")} fill="none" stroke="#facc15" strokeWidth="3" strokeLinecap="round" />
    <text x={left} y={height - 12} fill="#94a3b8" fontSize="11">{new Date(points[0].timestamp).toLocaleTimeString()}</text>
    <text x={width - right} y={height - 12} textAnchor="end" fill="#94a3b8" fontSize="11">{new Date(points.at(-1).timestamp).toLocaleTimeString()}</text>
  </svg></div>;
}

function ServerCard({ tab, updateHost, connect, disconnect }) {
  const latest = tab.logs[0];
  const error = usable(latest?.cpuTemperature) && usable(latest?.predictedTemperature) ? Math.abs(latest.cpuTemperature - latest.predictedTemperature) : null;
  return <article className="rounded-3xl border border-white/10 bg-slate-900/70 p-5">
    <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-bold">{tab.name}</h2><span className={tab.connected ? "text-sm text-emerald-300" : "text-sm text-slate-400"}>{tab.connected ? "● Live" : "● Offline"}</span></div>
    <div className="mb-4 flex gap-2"><input value={tab.backendHost} onChange={(e) => updateHost(e.target.value)} placeholder="Server IP or hostname" className="min-w-0 flex-1 rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm outline-none focus:border-amber-300" /><button onClick={tab.connected ? disconnect : connect} className="rounded-xl bg-white/10 px-3 py-2 text-sm font-semibold hover:bg-white/20">{tab.connected ? "Stop" : "Connect"}</button></div>
    <div className="grid grid-cols-2 gap-3"><Stat label="CPU temperature" value={temperature(latest?.cpuTemperature)} /><Stat label="Predicted temp" value={temperature(latest?.predictedTemperature)} /><Stat label="CPU usage" value={latest ? `${latest.cpuUsage}%` : "—"} /><Stat label="Target load" value={latest ? `${latest.targetLoad ?? "—"}%` : "—"} /><Stat label="CPU power" value={latest ? `${latest.cpuPackagePower} W` : "—"} /><Stat label="Prediction error" value={error === null ? "Warming up" : `${error.toFixed(2)}°C`} green={error !== null && error < 2} /></div>
    {tab.statusMessage ? <p className="mt-3 text-xs text-amber-200">{tab.statusMessage}</p> : null}
  </article>;
}

export default function PredictionDemo() {
  const { tabs, updateBackendHost, connect, disconnect } = useTelemetryTabs({ initialServerCount: 3 });
  const [targetLoad, setTargetLoad] = useState(50);
  const [message, setMessage] = useState("");
  const [chartServerId, setChartServerId] = useState(() => tabs[0].id);
  const chartTab = tabs.find((tab) => tab.id === chartServerId) || tabs[0];

  async function applyLoad() {
    const load = Number(targetLoad), hosts = tabs.map((tab) => hostForUrl(tab.backendHost)).filter(Boolean);
    if (!Number.isInteger(load) || load < 0 || load > 100) return setMessage("Enter a whole CPU target load from 0 to 100.");
    if (!hosts.length) return setMessage("Enter at least one server host before applying the load.");
    const results = await Promise.allSettled(hosts.map((host) => fetch(`http://${host}:8080/stress/target-load`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ targetLoad: load }) })));
    const failed = results.filter((result) => result.status === "rejected" || !result.value.ok).length;
    setMessage(failed ? `Target sent to ${hosts.length - failed}/${hosts.length} servers; check unreachable servers.` : `CPU target load set to ${load}% on ${hosts.length} server${hosts.length === 1 ? "" : "s"}.`);
  }

  return <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(250,204,21,.14),transparent_35%),linear-gradient(180deg,#07111f,#111827)] p-6 text-white"><div className="mx-auto max-w-7xl">
    <a href="/" className="text-sm text-slate-300 hover:text-white">← Telemetry dashboard</a><h1 className="mt-5 text-4xl font-bold">Temperature prediction demo</h1><p className="mt-2 text-slate-300">Green is current CPU temperature; yellow is the model prediction. Smaller separation means lower error.</p>
    <section className="mt-6 rounded-3xl border border-white/10 bg-slate-900/70 p-5"><label className="block text-sm font-semibold">Target CPU load (%)</label><div className="mt-2 flex flex-wrap gap-3"><input type="number" min="0" max="100" step="1" value={targetLoad} onChange={(e) => setTargetLoad(e.target.value)} className="w-44 rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 outline-none focus:border-amber-300" /><button onClick={applyLoad} className="rounded-xl bg-gradient-to-r from-amber-400 to-orange-500 px-5 py-3 font-bold text-slate-950">Apply to servers</button></div>{message ? <p className="mt-3 text-sm text-slate-300">{message}</p> : null}</section>
    <section className="mt-5 grid gap-5 lg:grid-cols-3">{tabs.map((tab) => <ServerCard key={tab.id} tab={tab} updateHost={(host) => updateBackendHost(tab.id, host)} connect={() => connect(tab.id, tab.backendHost)} disconnect={() => disconnect(tab.id)} />)}</section>
    <section className="mt-5 rounded-3xl border border-white/10 bg-slate-900/70 p-6"><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><div className="flex gap-5 text-sm"><span className="text-emerald-300">● Current temperature</span><span className="text-yellow-300">● Predicted temperature</span></div><label className="text-sm text-slate-300">Chart server <select value={chartTab.id} onChange={(e) => setChartServerId(e.target.value)} className="ml-2 rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 text-white">{tabs.map((tab) => <option key={tab.id} value={tab.id}>{tab.name}</option>)}</select></label></div><h2 className="mb-3 text-xl font-bold">Temperature vs time</h2><TemperatureChart logs={chartTab.logs} /></section>
  </div></main>;
}
