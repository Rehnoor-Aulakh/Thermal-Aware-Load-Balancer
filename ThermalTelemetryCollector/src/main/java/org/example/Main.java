package org.example;

import java.net.InetSocketAddress;

public class Main {

    private static final int TELEMETRY_INTERVAL_MS = 1000;
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
                    "Logged: "
                            + log.timestamp
                            + " | CPU Load: "
                            + log.cpuUsage
                            + "%"
                            + " | CPU Temp: "
                            + log.cpuTemperature
                            + " C"
                            + " | GPU Temp: "
                            + log.gpuTemperature
                            + " C"
                            + " | GPU Memory: "
                            + log.gpuMemoryLoad
                            + "%"
            );

                        Thread.sleep(TELEMETRY_INTERVAL_MS);
        }
    }
}
