package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates repeatable CPU-load profiles for collecting time-series telemetry.
 *
 * <p>The active target load is written to {@code current_tier.txt}. A separate
 * telemetry collector can read that file and attach {@code stressLevel} to
 * every hardware sample.</p>
 */
public final class StressTestRunner {

    private static final int PROCESSORS =
            Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final Path STRESS_LEVEL_FILE =
            Path.of("current_tier.txt");
    private static final List<Integer> DEFAULT_PROFILE =
            List.of(0, 0, 10, 10, 20, 30, 40, 50, 60, 70, 80, 90,
                    100, 100, 100, 90, 80, 70, 60, 50, 40, 30, 20,
                    10, 0, 0);
    private static final int CHAOS_STEPS_PER_MIXED_CYCLE = 16;
    private static final int SQUARE_STEPS_PER_MIXED_CYCLE = 10;

    private StressTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        Random random = new Random(config.seed());
        LoadSequence loads = new LoadSequence(config, random);
        AtomicBoolean stopRequested = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    stopRequested.set(true);
                    try {
                        writeStressLevel(0);
                    } catch (IOException error) {
                        System.err.println(
                                "Could not reset stress level during shutdown: "
                                        + error.getMessage());
                    }
                },
                "stress-shutdown"
        ));

        Instant finishAt = Instant.now().plus(config.duration());
        int stepNumber = 0;

        writeStressLevel(0);
        System.out.printf(
                Locale.ROOT,
                "Stress dataset run started: duration=%s, mode=%s, "
                        + "stepRange=%s-%s, seed=%d, processors=%d%n",
                formatDuration(config.duration()),
                config.mode().name().toLowerCase(Locale.ROOT),
                formatDuration(config.minimumStepDuration()),
                formatDuration(config.maximumStepDuration()),
                config.seed(),
                PROCESSORS
        );

        try {
            while (!stopRequested.get() && Instant.now().isBefore(finishAt)) {
                int load = loads.next(stepNumber);
                Duration remaining = Duration.between(Instant.now(), finishAt);
                Duration selectedStep = randomDuration(
                        random,
                        config.minimumStepDuration(),
                        config.maximumStepDuration()
                );
                Duration step = remaining.compareTo(selectedStep) < 0
                        ? remaining : selectedStep;

                if (step.isNegative() || step.isZero()) {
                    break;
                }

                writeStressLevel(load);
                System.out.printf(
                        Locale.ROOT,
                        "Step %d | targetLoad=%d%% | duration=%s | time=%s%n",
                        stepNumber + 1,
                        load,
                        formatDuration(step),
                        Instant.now()
                );
                runCpuLoad(load, step, stopRequested);
                stepNumber++;
            }
        } finally {
            writeStressLevel(0);
        }

        System.out.printf(
                Locale.ROOT,
                "Stress dataset run finished after %d steps.%n",
                stepNumber
        );
    }

    private static void runCpuLoad(
            int targetPercent,
            Duration duration,
            AtomicBoolean stopRequested
    ) throws InterruptedException {
        long endNanos = System.nanoTime() + duration.toNanos();

        if (targetPercent == 0) {
            waitUntil(endNanos, stopRequested);
            return;
        }

        AtomicBoolean workersRunning = new AtomicBoolean(true);
        ExecutorService workers = Executors.newFixedThreadPool(PROCESSORS);

        for (int i = 0; i < PROCESSORS; i++) {
            workers.submit(
                    () -> dutyCycleWorker(
                            targetPercent,
                            endNanos,
                            workersRunning,
                            stopRequested
                    )
            );
        }

        waitUntil(endNanos, stopRequested);
        workersRunning.set(false);
        workers.shutdownNow();
        workers.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void dutyCycleWorker(
            int targetPercent,
            long endNanos,
            AtomicBoolean workersRunning,
            AtomicBoolean stopRequested
    ) {
        long cycleNanos = TimeUnit.MILLISECONDS.toNanos(100);
        long busyNanos = cycleNanos * targetPercent / 100;
        double value = 0.5;

        while (workersRunning.get()
                && !stopRequested.get()
                && !Thread.currentThread().isInterrupted()
                && System.nanoTime() < endNanos) {
            long cycleStart = System.nanoTime();
            long busyUntil = Math.min(endNanos, cycleStart + busyNanos);

            while (System.nanoTime() < busyUntil
                    && workersRunning.get()
                    && !stopRequested.get()) {
                for (int i = 0; i < 1_000; i++) {
                    value = Math.sin(value) * Math.cos(value + i) + 1.000001;
                }
            }

            long sleepNanos = cycleNanos - (System.nanoTime() - cycleStart);
            if (sleepNanos > 0 && targetPercent < 100) {
                try {
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Retain the calculation so a JIT cannot safely discard the busy work.
        if (value == Double.MIN_VALUE) {
            System.out.print("");
        }
    }

    private static void waitUntil(
            long endNanos,
            AtomicBoolean stopRequested
    ) throws InterruptedException {
        while (!stopRequested.get()) {
            long remaining = endNanos - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            TimeUnit.NANOSECONDS.sleep(
                    Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250)));
        }
    }

    private static void writeStressLevel(int loadPercent) throws IOException {
        Path absoluteTarget = STRESS_LEVEL_FILE.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = Files.createTempFile(
                parent, "stress-level-", ".tmp");
        Files.writeString(
                temporary,
                Integer.toString(loadPercent),
                StandardCharsets.UTF_8
        );
        try {
            Files.move(
                    temporary,
                    absoluteTarget,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(
                    temporary,
                    absoluteTarget,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        return "%02d:%02d:%02d".formatted(
                seconds / 3600,
                seconds % 3600 / 60,
                seconds % 60
        );
    }

    private static Duration randomDuration(
            Random random,
            Duration minimum,
            Duration maximum
    ) {
        long minimumSeconds = minimum.toSeconds();
        long maximumSeconds = maximum.toSeconds();
        return Duration.ofSeconds(
                random.nextLong(minimumSeconds, maximumSeconds + 1));
    }

    private enum WorkloadMode {
        RAMP,
        CHAOS,
        SQUARE,
        MIXED,
        CUSTOM;

        private static WorkloadMode parse(String value) {
            try {
                WorkloadMode mode =
                        valueOf(value.toUpperCase(Locale.ROOT));
                if (mode == CUSTOM) {
                    throw new IllegalArgumentException(
                            "Use --profile to define a custom workload.");
                }
                return mode;
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                        "Mode must be ramp, chaos, square, or mixed: " + value);
            }
        }
    }

    private static final class LoadSequence {
        private final Config config;
        private final Random random;
        private int previousChaosLoad = -1;

        private LoadSequence(Config config, Random random) {
            this.config = config;
            this.random = random;
        }

        private int next(int stepNumber) {
            return switch (config.mode()) {
                case RAMP -> rampLoad(stepNumber);
                case CHAOS -> chaosLoad();
                case SQUARE -> squareLoad(stepNumber);
                case CUSTOM -> config.profile().get(
                        stepNumber % config.profile().size());
                case MIXED -> mixedLoad(stepNumber);
            };
        }

        private int mixedLoad(int stepNumber) {
            int cycleLength = DEFAULT_PROFILE.size()
                    + CHAOS_STEPS_PER_MIXED_CYCLE
                    + SQUARE_STEPS_PER_MIXED_CYCLE;
            int position = stepNumber % cycleLength;
            if (position < DEFAULT_PROFILE.size()) {
                return rampLoad(position);
            }
            if (position < DEFAULT_PROFILE.size()
                    + CHAOS_STEPS_PER_MIXED_CYCLE) {
                return chaosLoad();
            }
            return squareLoad(position);
        }

        private int rampLoad(int stepNumber) {
            return DEFAULT_PROFILE.get(stepNumber % DEFAULT_PROFILE.size());
        }

        private int squareLoad(int stepNumber) {
            return stepNumber % 2 == 0 ? 0 : 100;
        }

        private int chaosLoad() {
            int load;
            do {
                load = random.nextInt(11) * 10;
            } while (load == previousChaosLoad);
            previousChaosLoad = load;
            return load;
        }
    }

    private record Config(
            Duration duration,
            Duration minimumStepDuration,
            Duration maximumStepDuration,
            WorkloadMode mode,
            List<Integer> profile,
            long seed
    ) {
        private static Config parse(String[] args) {
            Duration duration = Duration.ofHours(6);
            Duration minimumStepDuration = Duration.ofSeconds(5);
            Duration maximumStepDuration = Duration.ofMinutes(5);
            WorkloadMode mode = WorkloadMode.MIXED;
            List<Integer> profile = List.of();
            long seed = System.nanoTime();
            boolean modeSpecified = false;
            boolean profileSpecified = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--duration-minutes" ->
                            duration = Duration.ofMinutes(
                                    parsePositiveLong(args, ++i, args[i - 1]));
                    case "--step-seconds" -> {
                        Duration fixed = Duration.ofSeconds(
                                parsePositiveLong(args, ++i, args[i - 1]));
                        minimumStepDuration = fixed;
                        maximumStepDuration = fixed;
                    }
                    case "--min-step-seconds" ->
                            minimumStepDuration = Duration.ofSeconds(
                                    parsePositiveLong(args, ++i, args[i - 1]));
                    case "--max-step-seconds" ->
                            maximumStepDuration = Duration.ofSeconds(
                                    parsePositiveLong(args, ++i, args[i - 1]));
                    case "--mode" -> {
                        mode = WorkloadMode.parse(
                                requireValue(args, ++i, args[i - 1]));
                        modeSpecified = true;
                    }
                    case "--profile" -> {
                        profile = parseProfile(
                                requireValue(args, ++i, args[i - 1]));
                        profileSpecified = true;
                    }
                    case "--seed" ->
                            seed = parseLong(args, ++i, args[i - 1]);
                    case "--help", "-h" -> {
                        printUsage();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException(
                            "Unknown argument: " + args[i] + ". Use --help.");
                }
            }

            if (modeSpecified && profileSpecified) {
                throw new IllegalArgumentException(
                        "Use either --mode or --profile, not both.");
            }
            if (profileSpecified) {
                mode = WorkloadMode.CUSTOM;
            }
            if (minimumStepDuration.compareTo(maximumStepDuration) > 0) {
                throw new IllegalArgumentException(
                        "--min-step-seconds cannot exceed --max-step-seconds.");
            }

            return new Config(
                    duration,
                    minimumStepDuration,
                    maximumStepDuration,
                    mode,
                    List.copyOf(profile),
                    seed
            );
        }

        private static long parsePositiveLong(
                String[] args,
                int index,
                String option
        ) {
            String value = requireValue(args, index, option);
            try {
                long parsed = Long.parseLong(value);
                if (parsed <= 0) {
                    throw new IllegalArgumentException(
                            option + " must be greater than zero.");
                }
                return parsed;
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                        option + " must be a whole number: " + value);
            }
        }

        private static long parseLong(
                String[] args,
                int index,
                String option
        ) {
            String value = requireValue(args, index, option);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                        option + " must be a whole number: " + value);
            }
        }

        private static String requireValue(
                String[] args,
                int index,
                String option
        ) {
            if (index >= args.length) {
                throw new IllegalArgumentException(
                        "Missing value after " + option);
            }
            return args[index];
        }

        private static List<Integer> parseProfile(String text) {
            List<Integer> levels = new ArrayList<>();
            for (String token : text.split(",")) {
                try {
                    int level = Integer.parseInt(token.trim());
                    if (level < 0 || level > 100 || level % 10 != 0) {
                        throw new IllegalArgumentException(
                                "Profile levels must be 0, 10, ... 100: "
                                        + token);
                    }
                    levels.add(level);
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException(
                            "Invalid profile level: " + token);
                }
            }
            if (levels.isEmpty()) {
                throw new IllegalArgumentException(
                        "Profile must contain at least one level.");
            }
            return levels;
        }

        private static void printUsage() {
            System.out.println("""
                    Usage: StressTestRunner [options]
                      --duration-minutes N  Total run time (default: 360)
                      --mode MODE           ramp, chaos, square, or mixed (default: mixed)
                      --min-step-seconds N  Minimum random step duration (default: 5)
                      --max-step-seconds N  Maximum random step duration (default: 300)
                      --step-seconds N      Use one fixed duration instead
                      --profile A,B,C       Use a custom repeating profile
                      --seed N              Reproduce the same random run

                    Example:
                      --duration-minutes 480 --mode mixed \
                    --min-step-seconds 2 --max-step-seconds 300 --seed 42
                    """);
        }
    }
}
