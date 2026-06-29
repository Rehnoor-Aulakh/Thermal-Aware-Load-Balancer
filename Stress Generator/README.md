# Stress Generator

Generates varied, reproducible CPU workloads for time-series
telemetry collection. This project intentionally does not collect telemetry;
run it alongside the separate Telemetry Collector.

## Workload modes

The default six-hour run uses `mixed` mode and randomly holds each load for
5–300 seconds. Mixed mode cycles through all three experiment types:

- `ramp`: gradual heating and cooling from 0% to 100% and back.
- `chaos`: unpredictable 0–100% jumps; consecutive values cannot repeat.
- `square`: alternates between 0% and 100% for thermal-shock boundaries.
- `mixed`: ramp, chaos, and square phases in one long collection.

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
  --mode mixed \
  --min-step-seconds 2 \
  --max-step-seconds 300 \
  --seed 42
```

Use `--mode ramp`, `--mode chaos`, or `--mode square` to isolate an
experiment. `--profile 10,90,30,100,0,50,20` defines a custom repeating
profile. `--step-seconds 60` intentionally restores a fixed duration.
Recording the seed makes a random run exactly reproducible.

Use `--help` to list the options. Press Ctrl+C to stop safely; the state file
is reset to `0`.

## Required telemetry features

For CPU-temperature forecasting, the collector should sample at a constant
interval and include at least:

- timestamp
- current CPU temperature
- actual CPU utilization percentage
- target load percentage from `current_tier.txt`
- CPU package power and frequency, when the hardware exposes them
- fan speed and ambient or inlet temperature, when sensors expose them

Target load and actual utilization are different signals and should not be
substituted for one another. The generator cannot model fan curves or ambient
temperature correctly; those external cooling effects must be measured during
collection. Collecting across different ambient conditions and fan modes will
make the model much more robust.

## Dataset boundary

The intended pipeline is:

```text
Stress Generator -> hardware load -> LibreHardwareMonitor
                 -> Telemetry Collector -> dataset.jsonl -> dataset.csv
```

The collector, JSONL/CSV writers, WebSocket server, and generated datasets do
not belong in this project.
