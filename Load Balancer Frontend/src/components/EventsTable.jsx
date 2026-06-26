export default function EventsTable({ events }) {
  return (
    <div
      className="
      rounded-3xl
      border border-white/10
      bg-slate-900/70
      p-6
    "
    >
      <h2 className="text-2xl font-bold mb-4">Connection Events</h2>

      <div className="overflow-auto max-h-[450px]">
        <table className="w-full">
          <thead>
            <tr className="border-b border-white/10">
              <th className="p-3 text-left">Timestamp</th>
              <th className="p-3 text-left">Type</th>
              <th className="p-3 text-left">Message</th>
            </tr>
          </thead>

          <tbody>
            {events.map((event) => (
              <tr
                key={event.id}
                className="
                border-b
                border-white/5
                hover:bg-white/5
              "
              >
                <td className="p-3">{event.timestamp}</td>

                <td className="p-3">{event.type}</td>

                <td className="p-3">{event.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
