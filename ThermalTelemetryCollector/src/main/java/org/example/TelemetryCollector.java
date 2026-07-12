package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oshi.SystemInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelemetryCollector {

        private static final URI LIBRE_HARDWARE_MONITOR_URL =
                        URI.create("http://192.168.1.90:8085/data.json");

        private static final Pattern NUMBER_PATTERN =
                        Pattern.compile("-?\\d+(?:\\.\\d+)?");

        private final SystemInfo si = new SystemInfo();
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpClient client = HttpClient.newHttpClient();

        private double cpuClockSum = 0;
        private int cpuClockCount = 0;
        private int cpuTemperaturePriority = -1;

        public SystemLog collect() {

                SystemLog log = new SystemLog();

                try {

                        log.timestamp = java.time.LocalDateTime.now().toString();

                        readOshiMetrics(log);
                        initializeLibreHardwareMonitorMetrics(log);
                        cpuClockSum = 0;
                        cpuClockCount = 0;
                        cpuTemperaturePriority = -1;

                        readLibreHardwareMonitor(log);

                } catch (Exception e) {

                        e.printStackTrace();
                }

                return log;
        }

        private void readOshiMetrics(SystemLog log) {

                var memory = si.getHardware().getMemory();

                log.ramUsage = round2(
                                ((double) (memory.getTotal() - memory.getAvailable()) / memory.getTotal()) * 100
                );

                var os = si.getOperatingSystem();

                log.networkConnections = os.getInternetProtocolStats().getConnections().size();
                log.processCount = os.getProcesses().size();
        }

        private void initializeLibreHardwareMonitorMetrics(SystemLog log) {

                log.cpuTemperature = -1;
                log.cpuPackagePower = -1;
                log.cpuVoltageCore1 = -1;
                log.cpuAverageClock = -1;
                log.cpuAverageEffectiveClock = -1;
                log.cpuFactors = new ArrayList<>();
                log.virtualMemoryUsage = -1;
                log.totalMemoryUsage = -1;
                log.gpuTemperature = -1;
                log.gpuCoreVoltage = -1;
                log.gpuCoreClock = -1;
                log.gpuMemoryClock = -1;
                log.gpuPackagePower = -1;
                log.gpuMemoryLoad = -1;
                log.ssdCompositeTemperature = -1;
        }

        private void readLibreHardwareMonitor(SystemLog log) {

                try {

                        HttpRequest request = HttpRequest.newBuilder().uri(LIBRE_HARDWARE_MONITOR_URL).build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        JsonNode root = mapper.readTree(response.body());

                        extractSensors(root, log);

                } catch (Exception e) {

                        e.printStackTrace();
                }
        }

        private void extractSensors(JsonNode node, SystemLog log) {

                if (node.has("Text") && node.has("Type") && node.has("Value")) {
                        mapSensor(node, log);
                }

                if (node.has("Children")) {
                        for (JsonNode child : node.get("Children")) {
                                extractSensors(child, log);
                        }
                }
        }

        private void mapSensor(JsonNode node, SystemLog log) {

                String text = node.get("Text").asText();
                String type = node.get("Type").asText();
                String sensorId = node.has("SensorId") ? node.get("SensorId").asText() : "";

                try {

                        double value = numericValue(node);

                        if (type.equals("Load") && text.equals("CPU Total")) {
                                log.cpuUsage = round2(value);
                        }

                        if (type.equals("Temperature")) {
                                if (text.equals("CPU Package") || text.equals("CPU Package Core")) {
                                        updateCpuTemperature(log, value, 3);
                                } else if (text.equals("Core Max")) {
                                        updateCpuTemperature(log, value, 2);
                                } else if (text.equals("Core (Tctl/Tdie)")) {
                                        updateCpuTemperature(log, value, 1);
                                }

                                if (sensorId.startsWith("/nvme/") && text.equals("Composite Temperature")) {
                                        log.ssdCompositeTemperature = round2(value);
                                }

                                if (text.equals("GPU Core") || (text.equals("GPU Hot Spot") && log.gpuTemperature < 0)) {
                                        log.gpuTemperature = round2(value);
                                }
                        }

                        if (type.equals("Power")) {
                                if (text.equals("CPU Package") || text.equals("Package")) {
                                        log.cpuPackagePower = round2(value);
                                }
                                if (text.equals("GPU Package") || text.equals("GPU Power")) {
                                        log.gpuPackagePower = round2(value);
                                }
                        }

                        if (type.equals("Voltage")) {
                                if (text.equals("CPU Core") || text.equals("Core #1 VID") || text.equals("Core #1")) {
                                        log.cpuVoltageCore1 = round2(value);
                                }
                                if (text.equals("GPU Core") || text.equals("GPU Core Voltage")) {
                                        log.gpuCoreVoltage = round2(value);
                                }
                        }

                        if (type.equals("Clock")) {
                                if (text.equals("Cores (Average)")) {
                                        log.cpuAverageClock = round2(value);
                                } else if (text.equals("Cores (Average Effective)")) {
                                        log.cpuAverageEffectiveClock = round2(value);
                                }

                                if (text.startsWith("P-Core") || text.startsWith("E-Core")) {
                                        cpuClockSum += value;
                                        cpuClockCount++;

                                        if (log.cpuAverageClock < 0) {
                                                log.cpuAverageClock = round2(cpuClockSum / cpuClockCount);
                                        }
                                }

                                if (text.equals("GPU Core")) {
                                        log.gpuCoreClock = round2(value);
                                } else if (text.equals("GPU Memory")) {
                                        log.gpuMemoryClock = round2(value);
                                }
                        }

                        if (type.equals("Load")) {
                                if (text.equals("Memory") && sensorId.startsWith("/vram/")) {
                                        log.virtualMemoryUsage = round2(value);
                                } else if (text.equals("Memory") && sensorId.startsWith("/ram/")) {
                                        log.totalMemoryUsage = round2(value);
                                } else if (text.equals("GPU Memory")) {
                                        log.gpuMemoryLoad = round2(value);
                                }
                        }

                        if (type.equals("Factor") && text.startsWith("Core #")) {
                                log.cpuFactors.add(round2(value));
                        }

                } catch (Exception ignored) {
                }
        }

        private void updateCpuTemperature(SystemLog log, double value, int priority) {

                if (priority >= cpuTemperaturePriority) {
                        cpuTemperaturePriority = priority;
                        log.cpuTemperature = round2(value);
                }
        }

        private double numericValue(JsonNode node) {

                String value = node.has("RawValue") ? node.get("RawValue").asText() : node.get("Value").asText();

                Matcher matcher = NUMBER_PATTERN.matcher(value);

                if (!matcher.find()) {
                        throw new IllegalArgumentException("No numeric value in sensor value: " + value);
                }

                return Double.parseDouble(matcher.group());
        }

        private double round2(double value) {
                return Math.round(value * 100.0) / 100.0;
        }
}