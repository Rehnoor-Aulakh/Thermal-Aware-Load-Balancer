package com.rehnoor.backendserveragent.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehnoor.backendserveragent.model.SystemLog;
import oshi.SystemInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelemetryCollector {

        private final URI libreHardwareMonitorUrl;

        private static final Pattern NUMBER_PATTERN =
                        Pattern.compile("-?\\d+(?:\\.\\d+)?");

        private final SystemInfo si = new SystemInfo();
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpClient client = HttpClient.newHttpClient();

        public TelemetryCollector(String libreHardwareMonitorUrl) {
                this.libreHardwareMonitorUrl = URI.create(libreHardwareMonitorUrl);
        }

        private double cpuClockSum = 0;
        private int cpuClockCount = 0;
        private double efficiencyClockSum = 0;
        private int efficiencyClockCount = 0;
        private boolean hasExposedAverageClock = false;
        private boolean hasExposedEfficiencyAverageClock = false;
        private int cpuTemperaturePriority = -1;

        public SystemLog collect() {

                SystemLog log = new SystemLog();

                try {

                        log.timestamp = java.time.LocalDateTime.now().toString();

                        readOshiMetrics(log);
                        initializeLibreHardwareMonitorMetrics(log);
                        cpuClockSum = 0;
                        cpuClockCount = 0;
                        efficiencyClockSum = 0;
                        efficiencyClockCount = 0;
                        hasExposedAverageClock = false;
                        hasExposedEfficiencyAverageClock = false;
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
                log.cpuEfficiencyAverageClock = -1;
                log.cpuFactors = new ArrayList<>();
                log.virtualMemoryUsage = -1;
                log.totalMemoryUsage = -1;
                log.gpuTemperature = -1;
                log.gpuCoreTemperature = -1;
                log.gpuHotspotTemperature = -1;
                log.gpuCoreLoad = -1;
                log.gpuCoreVoltage = -1;
                log.gpuPackagePower = -1;
                log.gpuCoreClock = -1;
                log.gpuMemoryClock = -1;
                log.gpuMemoryLoad = -1;
                log.ssdCompositeTemperature = -1;
        }

        private void readLibreHardwareMonitor(SystemLog log) {

                try {

                        HttpRequest request = HttpRequest.newBuilder().uri(libreHardwareMonitorUrl).build();

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

                                if (text.equals("GPU Core")) {
                                        log.gpuCoreTemperature = round2(value);
                                        log.gpuTemperature = log.gpuCoreTemperature;
                                } else if (text.equals("GPU Hot Spot")) {
                                        log.gpuHotspotTemperature = round2(value);
                                }
                        }

                        if (type.equals("Power")) {
                                if (text.equals("CPU Package") || text.equals("Package")) {
                                        log.cpuPackagePower = round2(value);
                                } else if (text.equals("GPU Package")) {
                                        log.gpuPackagePower = round2(value);
                                }
                                
                        }

                        if (type.equals("Voltage")) {
                                if (text.equals("CPU Core") || text.equals("Core #1 VID") || text.equals("Core #1")) {
                                        log.cpuVoltageCore1 = round2(value);
                                } else if (text.equals("GPU Core Voltage")) {
                                        log.gpuCoreVoltage = round2(value);
                                }
                                
                        }

                        if (type.equals("Clock")) {
                                if (text.equals("Cores (Average)")) {
                                        hasExposedAverageClock = true;
                                        log.cpuAverageClock = round2(value);
                                }

                                if (text.equals("Cores (Average Effective)")) {
                                        hasExposedEfficiencyAverageClock = true;
                                        log.cpuEfficiencyAverageClock = round2(value);
                                }

                                if (text.startsWith("P-Core")) {
                                        cpuClockSum += value;
                                        cpuClockCount++;

                                        if (!hasExposedAverageClock) {
                                                log.cpuAverageClock = round2(cpuClockSum / cpuClockCount);
                                        }
                                }

                                if (text.startsWith("E-Core")) {
                                        efficiencyClockSum += value;
                                        efficiencyClockCount++;

                                        if (!hasExposedEfficiencyAverageClock) {
                                                log.cpuEfficiencyAverageClock = round2(efficiencyClockSum / efficiencyClockCount);
                                        }
                                }

                                if (text.matches("Core #\\d+")) {
                                        cpuClockSum += value;
                                        cpuClockCount++;

                                        if (!hasExposedAverageClock) {
                                                log.cpuAverageClock = round2(cpuClockSum / cpuClockCount);
                                        }
                                }

                                if (text.matches("Core #\\d+ \\(Effective\\)")) {
                                        efficiencyClockSum += value;
                                        efficiencyClockCount++;

                                        if (!hasExposedEfficiencyAverageClock) {
                                                log.cpuEfficiencyAverageClock = round2(efficiencyClockSum / efficiencyClockCount);
                                        }
                                }

                                if (text.equals("GPU Core")) {
                                        log.gpuCoreClock = round2(value);
                                }

                                  if (text.equals("GPU Memory")) {
                                        log.gpuMemoryClock = round2(value);
                                }
                        }

                        if (type.equals("Load")) {
                                if (text.equals("Memory") && sensorId.startsWith("/vram/")) {
                                        log.virtualMemoryUsage = round2(value);
                                } else if (text.equals("Memory") && sensorId.startsWith("/ram/")) {
                                        log.totalMemoryUsage = round2(value);
                                } else if (text.equals("GPU Core")) {
                                        log.gpuCoreLoad = round2(value);
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