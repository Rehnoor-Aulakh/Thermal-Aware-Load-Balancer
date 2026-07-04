import { useState } from "react";

export default function ControlPanel({ connect, disconnect, connected }) {
  const [backendIp, setBackendIp] = useState("");

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
      <label className="block mb-3 text-slate-300">Backend Server IP</label>

      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={backendIp}
          onChange={(e) => setBackendIp(e.target.value)}
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
          onClick={() => connect(backendIp)}
          className="
          rounded-2xl
          px-6
          py-4
          font-bold
          bg-gradient-to-r
          from-amber-500
          to-pink-500
        "
        >
          Connect
        </button>

        <button
          disabled={!connected}
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
