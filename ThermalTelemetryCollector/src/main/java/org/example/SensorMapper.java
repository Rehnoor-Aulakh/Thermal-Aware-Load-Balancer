package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Maps vendor-neutral sensor names to the public telemetry model.
 *
 * <p>Rules prefer hardware family + sensor name. Exact IDs are retained only
 * as fallbacks for sensors whose names vary between LibreHardwareMonitor
 * versions.</p>
 */
public final class SensorMapper {

    private final SystemLog log;
    private final List<Rule> rules = new ArrayList<>();
    private double cpuClockTotal;
    private int cpuClockCount;

    public SensorMapper(SystemLog log) {
        this.log = log;
        registerRules();
    }

    public String map(Sensor sensor) {
        for (Rule rule : rules) {
            if (rule.matches().test(sensor)) {
                rule.update().accept(sensor);
                return rule.metric();
            }
        }
        return null;
    }

    public void finish() {
        if (cpuClockCount > 0) {
            log.cpuAverageClock = round2(cpuClockTotal / cpuClockCount);
        }
        if (log.gpuMemoryTotal > 0 && log.gpuMemoryUsed >= 0) {
            log.gpuMemoryLoad = round2(
                    log.gpuMemoryUsed / log.gpuMemoryTotal * 100.0);
        }
    }

    private void registerRules() {
        add("cpuUsage",
                sensor -> (isCpu(sensor)
                        && sensor.typeIs("Load")
                        && sensor.nameIs("CPU Total"))
                        || sensor.id().equalsIgnoreCase(
                                "/amdcpu/0/load/0"),
                sensor -> log.cpuUsage = round2(sensor.value()));

        add("cpuTemperature",
                sensor -> (isCpu(sensor)
                        && sensor.typeIs("Temperature")
                        && (sensor.nameContains("tctl/tdie")
                        || sensor.nameIs("CPU Package")))
                        || sensor.id().equalsIgnoreCase(
                                "/amdcpu/0/temperature/2"),
                sensor -> log.cpuTemperature = round2(sensor.value()));

        add("cpuPackagePower",
                sensor -> isCpu(sensor)
                        && sensor.typeIs("Power")
                        && (sensor.nameIs("Package")
                        || sensor.nameContains("CPU Package")
                        || sensor.id().equalsIgnoreCase(
                                "/amdcpu/0/power/0")),
                sensor -> log.cpuPackagePower = round2(sensor.value()));

        add("cpuAverageClock",
                sensor -> isCpu(sensor)
                        && sensor.typeIs("Clock")
                        && sensor.nameContains("CPU Core"),
                sensor -> {
                    cpuClockTotal += sensor.value();
                    cpuClockCount++;
                });

        add("cpuAverageClockFallback",
                sensor -> sensor.id().equalsIgnoreCase(
                        "/amdcpu/0/clock/1") && cpuClockCount == 0,
                sensor -> log.cpuAverageClock = round2(sensor.value()));

        add("gpuUsage",
                sensor -> (isGpu(sensor)
                        && sensor.typeIs("Load")
                        && (sensor.nameIs("GPU Core")
                        || sensor.nameIs("D3D 3D")))
                        || sensor.id().equalsIgnoreCase(
                                "/gpu-amd/0/load/0"),
                sensor -> log.gpuUsage = round2(sensor.value()));

        add("gpuTemperature",
                sensor -> isGpu(sensor)
                        && sensor.typeIs("Temperature")
                        && (sensor.nameIs("GPU Core")
                        || sensor.nameContains("GPU Hot Spot")),
                sensor -> log.gpuTemperature = round2(sensor.value()));

        add("gpuMemoryLoad",
                sensor -> isGpu(sensor)
                        && sensor.typeIs("Load")
                        && sensor.nameIs("GPU Memory"),
                sensor -> log.gpuMemoryLoad = round2(sensor.value()));

        add("gpuCoreClock",
                sensor -> (isGpu(sensor)
                        && sensor.typeIs("Clock")
                        && sensor.nameIs("GPU Core"))
                        || sensor.id().equalsIgnoreCase(
                                "/gpu-amd/0/clock/0"),
                sensor -> log.gpuCoreClock = round2(sensor.value()));

        add("gpuMemoryClock",
                sensor -> (isGpu(sensor)
                        && sensor.typeIs("Clock")
                        && sensor.nameContains("Memory"))
                        || sensor.id().equalsIgnoreCase(
                                "/gpu-amd/0/clock/2"),
                sensor -> log.gpuMemoryClock = round2(sensor.value()));

        add("gpuMemoryUsed",
                sensor -> isGpu(sensor)
                        && sensor.typeIs("SmallData")
                        && sensor.nameContains("memory used"),
                sensor -> log.gpuMemoryUsed = round2(sensor.value()));

        add("gpuMemoryUsedFallback",
                sensor -> sensor.id().equalsIgnoreCase(
                        "/gpu-amd/0/smalldata/0"),
                sensor -> log.gpuMemoryUsed = round2(sensor.value()));

        add("gpuMemoryTotal",
                sensor -> isGpu(sensor)
                        && sensor.typeIs("SmallData")
                        && sensor.nameContains("memory total"),
                sensor -> log.gpuMemoryTotal = round2(sensor.value()));

        add("gpuMemoryTotalFallback",
                sensor -> sensor.id().equalsIgnoreCase(
                        "/gpu-amd/0/smalldata/2"),
                sensor -> log.gpuMemoryTotal = round2(sensor.value()));

        add("ssdTemperature",
                sensor -> sensor.hasIdPrefix("/nvme/")
                        && sensor.typeIs("Temperature")
                        && (sensor.nameContains("temperature")
                        || sensor.nameContains("composite")),
                sensor -> log.ssdTemperature = round2(sensor.value()));

        add("ssdReadSpeed",
                sensor -> sensor.hasIdPrefix("/nvme/")
                        && sensor.typeIs("Throughput")
                        && sensor.nameContains("read"),
                sensor -> log.ssdReadSpeed = round2(sensor.value()));

        add("ssdWriteSpeed",
                sensor -> sensor.hasIdPrefix("/nvme/")
                        && sensor.typeIs("Throughput")
                        && sensor.nameContains("write"),
                sensor -> log.ssdWriteSpeed = round2(sensor.value()));

        add("networkUtilization",
                sensor -> sensor.hasIdPrefix("/nic/")
                        && sensor.typeIs("Load"),
                sensor -> log.networkUtilization = round2(sensor.value()));
    }

    private void add(
            String metric,
            Predicate<Sensor> matches,
            Consumer<Sensor> update
    ) {
        rules.add(new Rule(metric, matches, update));
    }

    private static boolean isCpu(Sensor sensor) {
        return sensor.hasIdPrefix("/amdcpu/")
                || sensor.hasIdPrefix("/intelcpu/")
                || sensor.hasIdPrefix("/cpu/");
    }

    private static boolean isGpu(Sensor sensor) {
        return sensor.hasIdPrefix("/gpu-amd/")
                || sensor.hasIdPrefix("/gpu-nvidia/")
                || sensor.hasIdPrefix("/gpu-intel/");
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Rule(
            String metric,
            Predicate<Sensor> matches,
            Consumer<Sensor> update
    ) {
    }
}
