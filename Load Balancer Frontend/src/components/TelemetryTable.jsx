export default function TelemetryTable({ logs }) {
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
      <h2 className="text-2xl font-bold mb-4">Telemetry Stream</h2>

      <div className="overflow-auto">
        <table className="w-full min-w-[900px]">
          <thead>
            <tr className="border-b border-white/10">
              <th className="p-3 text-left">Timestamp</th>

              <th className="p-3 text-left">CPU</th>

              <th className="p-3 text-left">CPU Temp</th>

              <th className="p-3 text-left">GPU Temp</th>

              <th className="p-3 text-left">GPU Mem</th>

              <th className="p-3 text-left">RAM</th>

              <th className="p-3 text-left">Net</th>

              <th className="p-3 text-left">Processes</th>

              <th className="p-3 text-left">CPU Power</th>

              <th className="p-3 text-left">CPU Clock</th>
            </tr>
          </thead>

          <tbody>
            {logs.map((log) => (
              <tr
                key={log.id}
                className="
                border-b
                border-white/5
                hover:bg-white/5
              "
              >
                <td className="p-3">{log.timestamp}</td>

                <td className="p-3">{log.cpuUsage}%</td>

                <td className="p-3">{log.cpuTemperature}°C</td>

                <td className="p-3">{log.gpuTemperature}°C</td>

                <td className="p-3">{log.gpuMemoryLoad}%</td>

                <td className="p-3">{log.ramUsage}%</td>

                <td className="p-3">{log.networkConnections}</td>

                <td className="p-3">{log.processCount}</td>

                <td className="p-3">{log.cpuPackagePower}</td>

                <td className="p-3"> {log.cpuAverageClock}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
