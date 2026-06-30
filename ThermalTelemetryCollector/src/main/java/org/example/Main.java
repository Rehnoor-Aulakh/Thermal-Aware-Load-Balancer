package org.example;

import java.net.InetSocketAddress;

public final class Main {

    private static final int TELEMETRY_INTERVAL_MS = 1000;
    private static final int TELEMETRY_WEBSOCKET_PORT = 8086;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        configure(args);

        TelemetryCollector collector = new TelemetryCollector();
        JsonLogger logger = new JsonLogger();
        TelemetryWebSocketServer telemetryServer =
                new TelemetryWebSocketServer(
                        new InetSocketAddress(TELEMETRY_WEBSOCKET_PORT));

        telemetryServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                telemetryServer.stop();
            } catch (Exception ignored) {
                // The process is already shutting down.
            }
        }));

        while (!Thread.currentThread().isInterrupted()) {
            long sampleStartedAt = System.currentTimeMillis();
            SystemLog log = collector.collect();
            String json = logger.write(log);
            telemetryServer.publish(json);

            System.out.printf(
                    "Logged %s | CPU %.2f%% %.2f C %.2f W | "
                            + "GPU %.2f%% %.2f C | RAM %.2f%% | target %.0f%%%n",
                    log.timestamp,
                    log.cpuUsage,
                    log.cpuTemperature,
                    log.cpuPackagePower,
                    log.gpuUsage,
                    log.gpuTemperature,
                    log.ramUsage,
                    log.targetLoad
            );

            long elapsed = System.currentTimeMillis() - sampleStartedAt;
            long delay = TELEMETRY_INTERVAL_MS - elapsed;
            if (delay > 0) {
                Thread.sleep(delay);
            }
        }
    }

    private static void configure(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--debug" ->
                        System.setProperty("telemetry.debug", "true");
                case "--lhm-url" ->
                        System.setProperty(
                                "telemetry.lhm.url",
                                requireValue(args, ++i, "--lhm-url"));
                case "--stress-file" ->
                        System.setProperty(
                                "telemetry.stress.file",
                                requireValue(args, ++i, "--stress-file"));
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException(
                        "Unknown argument: " + args[i] + ". Use --help.");
            }
        }
    }

    private static String requireValue(
            String[] args,
            int index,
            String option
    ) {
        if (index >= args.length) {
            throw new IllegalArgumentException(
                    "Missing value after " + option);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println("""
                Usage: Main [options]
                  --debug               Print discovered sensors and mappings
                  --lhm-url URL         LibreHardwareMonitor data.json URL
                  --stress-file PATH    Stress Generator current_tier.txt path

                Example:
                  Main --debug --lhm-url http://localhost:8085/data.json
                """);
    }
}
