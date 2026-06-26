export default function Header({ connectedCount, serverCount }) {
  return (
    <header
      className="
      rounded-[28px]
      border border-white/10
      bg-slate-900/70
      backdrop-blur-md
      p-8
      shadow-2xl
    "
    >
      <div
        className="
        inline-flex
        rounded-full
        bg-white/10
        px-4
        py-2
        text-xs
        uppercase
        tracking-[0.12em]
      "
      >
        Load Balancer Frontend
      </div>

      <h1
        className="
        mt-5
        text-4xl
        md:text-6xl
        font-bold
        leading-tight
        max-w-[12ch]
      "
      >
        Backend telemetry streamed through the relay
      </h1>

      <p
        className="
        mt-4
        max-w-4xl
        text-slate-300
        leading-8
      "
      >
        Open a tab for each backend server. Every tab keeps its own WebSocket
        alive, so switching between servers does not interrupt telemetry.
      </p>

      <div
        className={`
          inline-flex
          items-center
          gap-2
          rounded-full
          px-4
          py-2
          mt-5
          font-semibold
          ${
            connectedCount > 0
              ? "bg-green-500/10 text-green-300"
              : "bg-red-500/10 text-red-300"
          }
        `}
      >
        <span className="h-2.5 w-2.5 rounded-full bg-current" />
        {connectedCount} of {serverCount} servers connected
      </div>
    </header>
  );
}
