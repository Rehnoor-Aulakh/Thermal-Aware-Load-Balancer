# Stress Generator

Generates repeatable, smoothly changing CPU workloads for time-series
telemetry collection. This project intentionally does not collect telemetry;
run it alongside the separate Telemetry Collector.

## Default workload

The default run lasts six hours. Every 30 seconds it advances through a
repeating heat-up/cool-down profile:

```text
0, 0, 10, 10, 20, 30, 40, 50, 60, 70, 80, 90,
100, 100, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0, 0
```

At every transition, the generator atomically writes the active percentage
(`0` through `100`) to `current_tier.txt`. The Telemetry Collector should
read this file for every sample and store the value as `stressLevel`.

## Build and run

Run these commands from this directory:

```shell
mvn clean package
java -cp target/stress-generator-1.0-SNAPSHOT.jar org.example.StressTestRunner
```

Customize the collection run:

```shell
java -cp target/stress-generator-1.0-SNAPSHOT.jar org.example.StressTestRunner \
  --duration-minutes 480 \
  --step-seconds 60 \
  --profile 0,0,10,20,30,40,50,60,70,80,90,100,100,90,80,70,60,50,40,30,20,10,0
```

Use `--help` to list the options. Press Ctrl+C to stop safely; the state file
is reset to `0`.

## Dataset boundary

The intended pipeline is:

```text
Stress Generator -> hardware load -> LibreHardwareMonitor
                 -> Telemetry Collector -> dataset.jsonl -> dataset.csv
```

The collector, JSONL/CSV writers, WebSocket server, and generated datasets do
not belong in this project.
