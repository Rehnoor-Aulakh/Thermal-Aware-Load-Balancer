import Header from "./components/Header";
import ControlPanel from "./components/ControlPanel";
import StatusCard from "./components/StatusCard";
import EventsTable from "./components/EventsTable";
import TelemetryTable from "./components/TelemetryTable";
import TabsBar from "./components/TabsBar";
import useTelemetryTabs from "./hooks/useTelemetryTabs";

export default function App() {
  const {
    tabs,
    activeTab,
    activeTabId,
    setActiveTabId,
    addTab,
    closeTab,
    updateBackendHost,
    connect,
    disconnect,
  } = useTelemetryTabs();

  const connectedCount = tabs.filter((tab) => tab.connected).length;

  return (
    <div
      className="
      min-h-screen
      text-white
      bg-[radial-gradient(circle_at_top_left,rgba(255,195,113,0.18),transparent_35%),radial-gradient(circle_at_top_right,rgba(58,102,255,0.22),transparent_28%),linear-gradient(180deg,#07111f_0%,#0e1726_46%,#111827_100%)]
    "
    >
      <div className="max-w-7xl mx-auto p-6">
        <Header connectedCount={connectedCount} serverCount={tabs.length} />

        <TabsBar
          tabs={tabs}
          activeTabId={activeTabId}
          onSelectTab={setActiveTabId}
          onAddTab={addTab}
          onCloseTab={closeTab}
        />

        <ControlPanel
          tab={activeTab}
          connect={(backendHost) => connect(activeTab.id, backendHost)}
          disconnect={() => disconnect(activeTab.id)}
          updateBackendHost={(backendHost) =>
            updateBackendHost(activeTab.id, backendHost)
          }
        />

        <div className="grid lg:grid-cols-2 gap-5 mt-5">
          <div
            className="
            rounded-3xl
            border border-white/10
            bg-slate-900/70
            p-6
          "
          >
            <h2 className="text-2xl font-bold mb-4">Live Telemetry</h2>

            <StatusCard latest={activeTab.logs[0]} />
          </div>

          <EventsTable events={activeTab.events} />
        </div>

        <TelemetryTable logs={activeTab.logs} />
      </div>
    </div>
  );
}
