function Stat({ label, value }) {
  return (
    <div
      className="
      rounded-2xl
      border border-white/5
      bg-white/5
      p-4
    "
    >
      <p
        className="
        text-xs
        uppercase
        tracking-wider
        text-slate-400
        mb-2
      "
      >
        {label}
      </p>

      <p className="text-xl font-bold">{value}</p>
    </div>
  );
}

export default function StatusCard({ latest }) {
  if (!latest) {
    return <p className="text-slate-400">No telemetry received yet.</p>;
  }

  return (
    <div className="grid md:grid-cols-2 gap-3">
      <Stat label="Timestamp" value={latest.timestamp} />

      <Stat label="CPU Usage" value={`${latest.cpuUsage}%`} />

      <Stat label="CPU Temperature" value={`${latest.cpuTemperature}°C`} />

      <Stat label="GPU Temperature" value={`${latest.gpuTemperature}°C`} />

      <Stat label="GPU Memory Load" value={`${latest.gpuMemoryLoad}%`} />

      <Stat label="RAM Usage" value={`${latest.ramUsage}%`} />

      <Stat label="Network Connections" value={latest.networkConnections} />

      <Stat label="Process Count" value={latest.processCount} />

      <Stat label="CPU Power" value={latest.cpuPackagePower} />

      <Stat label="CPU Average Clock" value={latest.cpuAverageClock} />
    </div>
  );
}
