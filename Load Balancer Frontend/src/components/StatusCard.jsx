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

export default function StatusCard({ latest, statusMessage }) {
  if (!latest) {
    return (
      <div className="space-y-3">
        {statusMessage ? (
          <div className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-200">
            {statusMessage}
          </div>
        ) : (
          <p className="text-slate-400">No telemetry received yet.</p>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {statusMessage ? (
        <div className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-200">
          {statusMessage}
        </div>
      ) : null}

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
    </div>
  );
}
