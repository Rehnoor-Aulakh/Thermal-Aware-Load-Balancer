package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelemetryCollector {

    private static final URI LIBRE_HARDWARE_MONITOR_URL =
            URI.create("http://192.168.31.112:8085/data.json");

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final SystemInfo si =
            new SystemInfo();

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final HttpClient client =
            HttpClient.newHttpClient();

    public SystemLog collect() {

        SystemLog log =
                new SystemLog();

        try {

            log.timestamp =
                    java.time.LocalDateTime
                            .now()
                            .toString();

            readOshiMetrics(log);
            initializeLibreHardwareMonitorMetrics(log);
            readLibreHardwareMonitor(log);

        }
        catch (Exception e) {

            e.printStackTrace();
        }

        return log;
    }

    private double cpuClockSum = 0;
    private int cpuClockCount = 0;

    private void readOshiMetrics(SystemLog log)
            throws InterruptedException {

        var memory =
                si.getHardware()
                        .getMemory();

        log.ramUsage =
                round2(
                        ((double)
                                (memory.getTotal()
                                        - memory.getAvailable())
                                / memory.getTotal())
                                * 100
                );

        var os =
                si.getOperatingSystem();

        log.networkConnections =
                os.getInternetProtocolStats()
                        .getConnections()
                        .size();

        log.processCount =
                os.getProcesses()
                        .size();

        CentralProcessor cpu =
                si.getHardware()
                        .getProcessor();

        long[] ticks =
                cpu.getSystemCpuLoadTicks();

        Thread.sleep(1000);

        log.cpuUsage =
                round2(
                        cpu.getSystemCpuLoadBetweenTicks(ticks)
                                * 100
                );

    }

    private void initializeLibreHardwareMonitorMetrics(SystemLog log) {

        log.cpuTemperature = -1;
        log.cpuCoreMaxTemperature = -1;
        log.cpuPackagePower = -1;
        log.cpuAverageClock = -1;

        log.gpuTemperature = -1;
        log.gpuMemoryLoad = -1;
        log.gpuUsage = -1;
    }

    private void readLibreHardwareMonitor(SystemLog log) {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(LIBRE_HARDWARE_MONITOR_URL)
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            JsonNode root =
                    mapper.readTree(
                            response.body()
                    );

            extractSensors(
                    root,
                    log
            );

        }
        catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void extractSensors(
            JsonNode node,
            SystemLog log
    ) {

        if(node.has("Text")
                && node.has("Type")
                && node.has("Value")) {

            mapSensor(node, log);
        }

        if(node.has("Children")) {

            for(JsonNode child :
                    node.get("Children")) {

                extractSensors(
                        child,
                        log
                );
            }
        }
    }

    private void mapSensor(
            JsonNode node,
            SystemLog log
    ) {

        String text =
                node.get("Text")
                        .asText();

        String type =
                node.get("Type")
                        .asText();

        String sensorId =
                node.has("SensorId")
                        ? node.get("SensorId")
                        .asText()
                        : "";

        try {

            double value = numericValue(node);

            // ================= CPU Usage =================
            if (sensorId.equals("/intelcpu/0/load/0")
                    || sensorId.equals("/amdcpu/0/load/0")
                    || (text.equals("CPU Total") && type.equals("Load"))) {

                log.cpuUsage = round2(value);
            }

            // ================= CPU Temperature =================
            if (sensorId.equals("/intelcpu/0/temperature/18")
                    || sensorId.equals("/amdcpu/0/temperature/2")
                    || (text.equals("CPU Package") && type.equals("Temperature"))
                    || (text.equals("Core (Tctl/Tdie)") && type.equals("Temperature"))) {

                log.cpuTemperature = round2(value);
            }

            // ================= CPU Package Power =================
            if (sensorId.equals("/intelcpu/0/power/0")
                    || sensorId.equals("/amdcpu/0/power/0")) {

                log.cpuPackagePower = round2(value);
            }

            // ================= CPU Average Clock =================
            if ((sensorId.equals("/intelcpu/0/clock/0") && text.equals("Bus Speed"))
                    || (sensorId.equals("/amdcpu/0/clock/1"))) {

                log.cpuAverageClock = round2(value);
            }

            // ================= GPU Temperature =================
            if (sensorId.equals("/gpu-nvidia/0/temperature/0")
                    || sensorId.equals("/gpu-nvidia/0/temperature/2")
                    || (text.equals("GPU Core") && type.equals("Temperature"))
                    || (text.equals("GPU Hot Spot") && type.equals("Temperature"))) {

                log.gpuTemperature = round2(value);
            }

            // ================= GPU Memory Usage =================
            if (sensorId.equals("/gpu-nvidia/0/load/3")
                    || (text.equals("GPU Memory") && type.equals("Load"))) {

                log.gpuMemoryLoad = round2(value);
            }

            // ================= GPU Core Usage =================
            if (sensorId.equals("/gpu-nvidia/0/load/0")
                    || (text.equals("GPU Core") && type.equals("Load"))) {

                log.gpuUsage = round2(value);
            }
            if (sensorId.equals("/intelcpu/0/temperature/0")
                    || text.equals("Core Max")) {

                log.cpuCoreMaxTemperature = round2(value);
            }
            if (type.equals("Clock")) {

                if (text.startsWith("P-Core")
                        || text.startsWith("E-Core")) {

                    cpuClockSum += value;
                    cpuClockCount++;

                    log.cpuAverageClock =
                            round2(cpuClockSum / cpuClockCount);
                }
            }

        }
        catch (Exception ignored) {
        }
    }

    private double numericValue(JsonNode node) {

        String value =
                node.has("RawValue")
                        ? node.get("RawValue")
                        .asText()
                        : node.get("Value")
                        .asText();

        Matcher matcher =
                NUMBER_PATTERN.matcher(value);

        if(!matcher.find()) {
            throw new IllegalArgumentException(
                    "No numeric value in sensor value: " + value
            );
        }

        return Double.parseDouble(
                matcher.group()
        );
    }

    private double round2(double value) {

        return Math.round(value * 100.0) / 100.0;
    }
}