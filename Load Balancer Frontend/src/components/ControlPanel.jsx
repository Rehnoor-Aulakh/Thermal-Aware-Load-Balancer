export default function ControlPanel({
  tab,
  connect,
  disconnect,
  updateBackendHost,
}) {
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
        <label className="text-slate-300">Backend Server IP</label>

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

      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={tab.backendHost}
          onChange={(e) => updateBackendHost(e.target.value)}
          placeholder="192.168.1.50"
          className="
          flex-1
          min-w-[280px]
          rounded-2xl
          border border-slate-700
          bg-slate-950
          px-4
          py-4
          outline-none
          focus:border-blue-400
        "
        />

        <button
          onClick={() => connect(tab.backendHost)}
          disabled={tab.connecting}
          className="
          rounded-2xl
          px-6
          py-4
          font-bold
          bg-gradient-to-r
          from-amber-500
          to-pink-500
          disabled:opacity-50
        "
        >
          {tab.connecting ? "Connecting" : "Connect"}
        </button>

        <button
          disabled={!tab.connected && !tab.connecting}
          onClick={disconnect}
          className="
          rounded-2xl
          px-6
          py-4
          font-bold
          bg-white/10
          disabled:opacity-40
        "
        >
          Disconnect
        </button>
      </div>
    </section>
  );
}
