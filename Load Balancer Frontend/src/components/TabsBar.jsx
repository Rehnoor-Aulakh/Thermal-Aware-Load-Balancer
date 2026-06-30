function getStatusColor(tab) {
  if (tab.connected) {
    return "bg-green-400";
  }

  if (tab.connecting) {
    return "bg-amber-300";
  }

  return "bg-red-400";
}

export default function TabsBar({
  tabs,
  activeTabId,
  onSelectTab,
  onAddTab,
  onCloseTab,
}) {
  return (
    <nav
      className="
      mt-5
      flex
      items-end
      gap-1
      overflow-x-auto
      border-b
      border-white/10
      px-2
    "
      aria-label="Server tabs"
    >
      {tabs.map((tab) => {
        const active = tab.id === activeTabId;

        return (
          <div
            key={tab.id}
            onMouseDown={(e) => {
              if (e.button == 1) {
                e.preventDefault();
                onCloseTab(tab.id);
              }
            }}
            className={`
              group
              flex
              min-w-[150px]
              max-w-[220px]
              items-center
              gap-2
              rounded-t-lg
              border
              border-b-0
              px-3
              py-2.5
              ${
                active
                  ? "border-white/15 bg-slate-900 text-white"
                  : "border-white/5 bg-slate-950/70 text-slate-300 hover:bg-slate-900/80"
              }
            `}
          >
            <button
              type="button"
              onClick={() => onSelectTab(tab.id)}
              className="flex min-w-0 flex-1 items-center gap-2 text-left"
              title={tab.name}
            >
              <span
                className={`h-2.5 w-2.5 shrink-0 rounded-full ${getStatusColor(tab)}`}
              />

              <span className="truncate text-sm font-semibold">{tab.name}</span>
            </button>

            <button
              type="button"
              onClick={() => onCloseTab(tab.id)}
              className="
              grid
              h-6
              w-6
              shrink-0
              place-items-center
              rounded-md
              text-slate-400
              hover:bg-white/10
              hover:text-white
            "
              aria-label={`Close ${tab.name}`}
              title="Close tab"
            >
              x
            </button>
          </div>
        );
      })}

      <button
        type="button"
        onClick={onAddTab}
        className="
        mb-1
        grid
        h-9
        w-9
        shrink-0
        place-items-center
        rounded-lg
        border
        border-white/10
        bg-white/10
        text-xl
        font-semibold
        text-slate-200
        hover:bg-white/15
      "
      >
        +
      </button>
    </nav>
  );
}
