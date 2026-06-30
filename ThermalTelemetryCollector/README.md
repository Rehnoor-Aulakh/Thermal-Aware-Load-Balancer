# Thermal Telemetry Collector

Collects operating-system metrics with OSHI and hardware sensors from
LibreHardwareMonitor, writes JSONL, and publishes the latest sample over
WebSocket.

## Run

Start LibreHardwareMonitor's remote web server, then run `org.example.Main`.
The current default endpoint is:

```text
http://192.168.29.204:8085/data.json
```

Override it without changing source code:

```shell
java -cp <classpath> org.example.Main \
  --lhm-url http://localhost:8085/data.json
```

The collector reads the Stress Generator's numeric target from
`../Stress Generator/current_tier.txt`. Override that location with:

```shell
--stress-file "/full/path/to/current_tier.txt"
```

## Debugging sensors

Run once with debug mode:

```shell
java -cp <classpath> org.example.Main --debug
```

Each discovered sensor is printed once:

```text
[telemetry:sensor] /gpu-amd/0/load/0 | GPU Core | Load | 5.00 | -> gpuUsage
```

An `unmapped` suffix means LibreHardwareMonitor exposed the sensor but no
dataset field currently uses it. Connection and malformed-value errors use
the `[telemetry:error]` prefix; debug mode includes their stack traces.

The same configuration can be supplied through environment variables:

- `LHM_URL`
- `STRESS_LEVEL_FILE`
- `TELEMETRY_DEBUG=true`

or Java properties:

- `-Dtelemetry.lhm.url=...`
- `-Dtelemetry.stress.file=...`
- `-Dtelemetry.debug=true`

## AMD system behavior

The mapper supports the Ryzen/AMD Radeon IDs observed on this system and also
matches stable sensor names so sensor-number changes are less likely to break
collection. It records CPU temperature, load, package power and clocks; AMD
GPU load, clocks and memory; NVMe temperature/throughput; and network load
when LibreHardwareMonitor exposes them.

This system's JSON does not expose an AMD GPU temperature sensor. Therefore
`gpuTemperature` remains `-1`, meaning unavailable. It must not be replaced
with zero because zero would be interpreted as a real training value.
