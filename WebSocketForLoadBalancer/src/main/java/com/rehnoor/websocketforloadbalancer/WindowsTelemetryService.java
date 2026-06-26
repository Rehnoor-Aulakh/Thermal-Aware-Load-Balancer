package com.rehnoor.websocketforloadbalancer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WindowsTelemetryService {

    private final ObjectMapper objectMapper;

    public WindowsTelemetryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> collectMetrics(int fanSpeed) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("fanSpeed", fanSpeed);

        if (!isWindows()) {
            metrics.put("source", "fallback");
            metrics.put("message", "Telemetry source is unavailable on this OS; run the backend on Windows to read real hardware counters.");
            metrics.put("cpu_usage", null);
            metrics.put("gpu_usage", null);
            metrics.put("memory_usage", null);
            metrics.put("cpu_temperature", null);
            metrics.put("gpu_temperature", null);
            metrics.put("other_temperatures", new LinkedHashMap<>());
            metrics.put("sample_timestamp", Instant.now().toString());
            return metrics;
        }

        Map<String, Object> hardwareSnapshot = readWindowsHardwareSnapshot();
        metrics.putAll(hardwareSnapshot);
        return metrics;
    }

    private Map<String, Object> readWindowsHardwareSnapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("source", "windows");
        metrics.put("sample_timestamp", Instant.now().toString());

        double cpuUsage = readPowerShellDouble(
                "(Get-Counter '\\Processor(_Total)\\% Processor Time').CounterSamples[0].CookedValue"
        );
        double memoryUsage = readPowerShellDouble(
                "(Get-Counter '\\Memory\\% Committed Bytes In Use').CounterSamples[0].CookedValue"
        );
        double gpuUsage = readGpuUsage();

        Double cpuTemperature = readTemperatureFromSensors(List.of("cpu", "package", "tdie", "tctl"));
        Double gpuTemperature = readTemperatureFromSensors(List.of("gpu"));
        Map<String, Object> otherTemperatures = readOtherTemperatures();

        metrics.put("cpu_usage", round(cpuUsage));
        metrics.put("gpu_usage", round(gpuUsage));
        metrics.put("memory_usage", round(memoryUsage));
        metrics.put("cpu_temperature", cpuTemperature == null ? null : round(cpuTemperature));
        metrics.put("gpu_temperature", gpuTemperature == null ? null : round(gpuTemperature));
        metrics.put("other_temperatures", otherTemperatures);

        if (cpuTemperature == null && gpuTemperature == null && otherTemperatures.isEmpty()) {
            metrics.put("temperature_note", "No hardware sensor provider was found. For reliable CPU/GPU temperatures on Windows gaming laptops, install LibreHardwareMonitor or a similar sensor service.");
        }

        return metrics;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private double readGpuUsage() {
        String script = "try { " +
                "$samples = Get-Counter '\\GPU Engine(*)\\Utilization Percentage' -ErrorAction Stop; " +
                "$values = $samples.CounterSamples | Select-Object -ExpandProperty CookedValue; " +
                "if ($values) { [Math]::Min(100, [Math]::Round((($values | Measure-Object -Average).Average), 2)) } else { 0 } " +
                "} catch { 0 }";
        return readPowerShellDouble(script);
    }

    private Double readTemperatureFromSensors(List<String> keywords) {
        for (JsonNode sensor : readTemperatureSensorNodes()) {
            String candidate = (textValue(sensor, "Name") + " " + textValue(sensor, "Identifier")).toLowerCase();
            for (String keyword : keywords) {
                if (candidate.contains(keyword.toLowerCase())) {
                    Double sensorValue = doubleValue(sensor, "Value");
                    if (sensorValue != null) {
                        return sensorValue;
                    }
                }
            }
        }

        return readAcpiTemperature();
    }

    private List<JsonNode> readTemperatureSensorNodes() {
        String script = "try { " +
                "$namespaces = @('root/LibreHardwareMonitor', 'root/OpenHardwareMonitor'); " +
                "foreach ($ns in $namespaces) { " +
                "  $sensors = Get-CimInstance -Namespace $ns -ClassName Sensor -ErrorAction SilentlyContinue | Where-Object { $_.SensorType -eq 'Temperature' } | Select-Object Name, Identifier, Value, SensorType; " +
                "  if ($sensors) { " +
                "    $sensors | ConvertTo-Json -Depth 4; exit; " +
                "  } " +
                "} " +
                "'[]' " +
                "} catch { '[]' }";

        String output = readPowerShellOutput(script);
        if (output == null || output.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(output);
            if (root == null || root.isNull()) {
                return List.of();
            }

            if (root.isArray()) {
                List<JsonNode> sensors = new java.util.ArrayList<>();
                for (JsonNode node : root) {
                    sensors.add(node);
                }
                return sensors;
            }

            return List.of(root);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readOtherTemperatures() {
        Map<String, Object> temperatures = new LinkedHashMap<>();

        Double ssdTemperature = readTemperatureFromSensors(List.of("ssd", "nvme", "m.2"));
        Double vrmTemperature = readTemperatureFromSensors(List.of("vrm"));
        Double batteryTemperature = readTemperatureFromSensors(List.of("battery"));
        Double chipsetTemperature = readTemperatureFromSensors(List.of("chipset", "pch"));

        putIfPresent(temperatures, "ssd_temperature", ssdTemperature);
        putIfPresent(temperatures, "vrm_temperature", vrmTemperature);
        putIfPresent(temperatures, "battery_temperature", batteryTemperature);
        putIfPresent(temperatures, "chipset_temperature", chipsetTemperature);

        return temperatures;
    }

    private void putIfPresent(Map<String, Object> target, String key, Double value) {
        if (value != null) {
            target.put(key, round(value));
        }
    }

    private double readPowerShellDouble(String script) {
        Double value = readPowerShellNullableDouble(script);
        return value == null ? 0.0 : value;
    }

    private Double readAcpiTemperature() {
        String script = "try { " +
                "$thermal = Get-CimInstance -Namespace root/wmi -ClassName MSAcpi_ThermalZoneTemperature -ErrorAction SilentlyContinue | Select-Object -First 1; " +
                "if ($thermal) { [Math]::Round(($thermal.CurrentTemperature / 10.0) - 273.15, 2) } else { $null } " +
                "} catch { $null }";
        return readPowerShellNullableDouble(script);
    }

    private String readPowerShellOutput(String script) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                script
        );
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            process.waitFor();
            return output.toString().trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private Double readPowerShellNullableDouble(String script) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                script
        );
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }

            process.waitFor();

            if (output == null || output.isBlank()) {
                return null;
            }

            String trimmed = output.trim();
            if (trimmed.equalsIgnoreCase("null")) {
                return null;
            }

            return Double.parseDouble(trimmed);
        } catch (Exception ex) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode == null || valueNode.isNull() ? "" : valueNode.asText("");
    }

    private Double doubleValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.isNumber()) {
            return null;
        }
        return valueNode.asDouble();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}