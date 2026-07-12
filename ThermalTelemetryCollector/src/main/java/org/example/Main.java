package org.example;

import java.net.InetSocketAddress;

public class Main {

    private static final int TELEMETRY_INTERVAL_MS = 2000;
    private static final int TELEMETRY_WEBSOCKET_PORT = 8086;

    public static void main(String[] args)
            throws Exception {

        TelemetryCollector collector =
                new TelemetryCollector();

        JsonLogger logger =
                new JsonLogger();

        TelemetryWebSocketServer telemetryServer =
                new TelemetryWebSocketServer(
                        new InetSocketAddress(
                                TELEMETRY_WEBSOCKET_PORT
                        )
                );

        telemetryServer.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        telemetryServer.stop();
                    }
                    catch (Exception ignored) {
                    }
                })
        );

        while (true) {

            SystemLog log =
                    collector.collect();

            String json =
                    logger.write(log);

            telemetryServer.publish(json);

            System.out.println(
                    "\n================ TELEMETRY ================\n" +

                            "Timestamp              : " + log.timestamp + "\n\n" +

                            "CPU Usage              : " + log.cpuUsage + " %\n" +
                            "CPU Temperature        : " + log.cpuTemperature + " °C\n" +
                            "CPU Package Power      : " + log.cpuPackagePower + " W\n" +
                            "CPU Voltage Core #1    : " + log.cpuVoltageCore1 + " V\n" +
                            "CPU Average Clock      : " + log.cpuAverageClock + " MHz\n\n" +
                           


                            "Virtual Memory Load    : " + log.virtualMemoryUsage + " %\n" +
                            "Total Memory Load      : " + log.totalMemoryUsage + " %\n" +
                            "RAM Usage              : " + log.ramUsage + " %\n" +
                            "GPU Memory Clock       : " + log.gpuMemoryClock + " MHz\n" +
                            "SSD Composite Temp     : " + log.ssdCompositeTemperature + " °C\n" +
                            "Processes              : " + log.processCount + "\n" +
                            "Network Connections    : " + log.networkConnections + "\n" +

                            "=========================================="
            );

            Thread.sleep(TELEMETRY_INTERVAL_MS);
        }
    }
}