package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combines portable OSHI metrics with LibreHardwareMonitor sensor data.
 */
public final class TelemetryCollector {

    private static final URI DEFAULT_MONITOR_URL =
            URI.create("http://192.168.29.204:8085/data.json");
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Path DEFAULT_STRESS_LEVEL_FILE =
            Path.of("..", "Stress Generator", "current_tier.txt");

    private final SystemInfo systemInfo = new SystemInfo();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI monitorUrl;
    private final boolean debug;
    private final Path stressLevelFile;
    private final Set<String> reportedSensors = new HashSet<>();

    public TelemetryCollector() {
        this(
                configuredMonitorUrl(),
                configuredStressLevelFile(),
                debugEnabled()
        );
    }

    public TelemetryCollector(
            URI monitorUrl,
            Path stressLevelFile,
            boolean debug
    ) {
        this.monitorUrl = monitorUrl;
        this.stressLevelFile = stressLevelFile;
        this.debug = debug;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        debug("LibreHardwareMonitor URL: " + monitorUrl);
        debug("Stress level file: "
                + stressLevelFile.toAbsolutePath().normalize());
    }

    public SystemLog collect() {
        SystemLog log = new SystemLog();
        log.timestamp = LocalDateTime.now().toString();
        initializeMissingMetrics(log);

        collectOshiMetrics(log);
        collectLibreHardwareMetrics(log);
        collectTargetLoad(log);
        return log;
    }

    private void collectTargetLoad(SystemLog log) {
        try {
            if (!Files.exists(stressLevelFile)) {
                debug("Stress level file does not exist; targetLoad=-1");
                return;
            }
            String value = Files.readString(stressLevelFile).trim();
            double targetLoad = Double.parseDouble(value);
            if (targetLoad < 0 || targetLoad > 100) {
                throw new IllegalArgumentException(
                        "target load is outside 0-100: " + value);
            }
            log.targetLoad = round2(targetLoad);
        } catch (IOException | IllegalArgumentException error) {
            reportError(
                    "Cannot read stress level from "
                            + stressLevelFile.toAbsolutePath().normalize(),
                    error
            );
        }
    }

    private void collectOshiMetrics(SystemLog log) {
        try {
            var hardware = systemInfo.getHardware();
            var memory = hardware.getMemory();
            log.ramUsage = round2(
                    (double) (memory.getTotal() - memory.getAvailable())
                            / memory.getTotal() * 100.0);

            var operatingSystem = systemInfo.getOperatingSystem();
            log.networkConnections =
                    operatingSystem.getInternetProtocolStats()
                            .getConnections().size();
            log.processCount = operatingSystem.getProcessCount();

            CentralProcessor cpu = hardware.getProcessor();
            log.cpuUsage = round2(cpu.getSystemCpuLoad(1000) * 100.0);
        } catch (RuntimeException error) {
            reportError("OSHI collection failed", error);
        }
    }

    private void collectLibreHardwareMetrics(SystemLog log) {
        HttpRequest request = HttpRequest.newBuilder(monitorUrl)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                reportError(
                        "LibreHardwareMonitor returned HTTP "
                                + response.statusCode(),
                        null
                );
                return;
            }

            SensorMapper sensorMapper = new SensorMapper(log);
            extractSensors(json.readTree(response.body()), sensorMapper);
            sensorMapper.finish();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            reportError("LibreHardwareMonitor request was interrupted",
                    interrupted);
        } catch (IOException | RuntimeException error) {
            reportError(
                    "Cannot read LibreHardwareMonitor at " + monitorUrl,
                    error
            );
        }
    }

    private void extractSensors(JsonNode node, SensorMapper sensorMapper) {
        if (isSensor(node)) {
            try {
                Sensor sensor = new Sensor(
                        node.path("SensorId").asText(""),
                        node.path("Text").asText(""),
                        node.path("Type").asText(""),
                        numericValue(node)
                );
                String metric = sensorMapper.map(sensor);
                debugSensor(sensor, metric);
            } catch (IllegalArgumentException error) {
                reportError(
                        "Invalid sensor " + node.path("SensorId").asText("?"),
                        error
                );
            }
        }

        JsonNode children = node.get("Children");
        if (children != null && children.isArray()) {
            children.forEach(child -> extractSensors(child, sensorMapper));
        }
    }

    private static boolean isSensor(JsonNode node) {
        return node.has("Text")
                && node.has("Type")
                && (node.has("RawValue") || node.has("Value"));
    }

    private static double numericValue(JsonNode node) {
        String raw = node.hasNonNull("RawValue")
                ? node.get("RawValue").asText()
                : node.path("Value").asText();
        Matcher matcher = NUMBER_PATTERN.matcher(raw);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "No number found in value '" + raw + "'");
        }
        return Double.parseDouble(matcher.group().replace(',', '.'));
    }

    private void debugSensor(Sensor sensor, String metric) {
        if (!debug || !reportedSensors.add(sensor.id())) {
            return;
        }
        String mapping = metric == null ? "unmapped" : "-> " + metric;
        System.out.printf(
                "[telemetry:sensor] %-36s | %-18s | %-12s | %10.2f | %s%n",
                sensor.id(),
                sensor.name(),
                sensor.type(),
                sensor.value(),
                mapping
        );
    }

    private void debug(String message) {
        if (debug) {
            System.out.println("[telemetry:debug] " + message);
        }
    }

    private void reportError(String message, Exception error) {
        System.err.println("[telemetry:error] " + message);
        if (debug && error != null) {
            error.printStackTrace(System.err);
        } else if (error != null && error.getMessage() != null) {
            System.err.println("[telemetry:error] " + error.getMessage());
        }
    }

    private static void initializeMissingMetrics(SystemLog log) {
        log.cpuTemperature = -1;
        log.gpuTemperature = -1;
        log.gpuMemoryLoad = -1;
        log.gpuUsage = -1;
        log.cpuPackagePower = -1;
        log.cpuAverageClock = -1;
        log.gpuCoreClock = -1;
        log.gpuMemoryClock = -1;
        log.gpuMemoryUsed = -1;
        log.gpuMemoryTotal = -1;
        log.ssdTemperature = -1;
        log.ssdReadSpeed = -1;
        log.ssdWriteSpeed = -1;
        log.networkUtilization = -1;
        log.targetLoad = -1;
    }

    private static URI configuredMonitorUrl() {
        String configured = System.getProperty(
                "telemetry.lhm.url",
                System.getenv().getOrDefault(
                        "LHM_URL",
                        DEFAULT_MONITOR_URL.toString())
        );
        return URI.create(configured);
    }

    private static boolean debugEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                "telemetry.debug",
                System.getenv().getOrDefault("TELEMETRY_DEBUG", "false")
        ));
    }

    private static Path configuredStressLevelFile() {
        return Path.of(System.getProperty(
                "telemetry.stress.file",
                System.getenv().getOrDefault(
                        "STRESS_LEVEL_FILE",
                        DEFAULT_STRESS_LEVEL_FILE.toString())
        ));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
