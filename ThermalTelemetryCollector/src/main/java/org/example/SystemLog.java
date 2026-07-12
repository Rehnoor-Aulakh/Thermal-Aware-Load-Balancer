package org.example;

import java.util.ArrayList;
import java.util.List;

public class SystemLog {

    public String timestamp;

    public double cpuUsage;
    public double cpuTemperature;
    public double cpuPackagePower;
    public double cpuVoltageCore1;
    public double cpuAverageClock;
    public List<Double> cpuFactors = new ArrayList<>();

    public double virtualMemoryUsage;
    public double totalMemoryUsage;

    public double ramUsage;

    public double gpuTemperature;
    public double gpuMemoryClock;
    public double gpuMemoryLoad;

    public double ssdCompositeTemperature;

    public int networkConnections;
    public int processCount;
}