package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic CPU + GPU stress dataset generator for LSTM temperature forecasting.
 *
 * This program is completely independent of the thermal telemetry collector.
 *
 * It generates CPU and GPU workload and stores its own verification logs only.
 *
 * CPU load is created via duty-cycle spinning across all available processors.
 * GPU load is created via OpenCL tensor (matrix) multiplications.
 * Both run in parallel for every workload step.
 *
 * If no OpenCL-capable GPU is detected, the program falls back to
 * CPU-only mode with a warning.
 *
 * One execution automatically performs:
 *
 * 1. Initial idle
 * 2. Ramp workload                          (CPU only)
 * 3. Cooling
 * 4. GPU_CPU_COMBINED workload (30 minutes) (CPU + GPU)
 * 5. Cooling
 * 6. Chaos workload                         (CPU only)
 * 7. Cooling
 * 8. GPU_CPU_COMBINED workload (15 minutes) (CPU + GPU)
 * 9. Cooling
 * 10. Transition workload                   (CPU only)
 * 11. Cooling
 * 12. GPU_CPU_COMBINED workload (15 minutes) (CPU + GPU)
 * 13. Cooling
 * 14. Mixed workload                        (CPU only)
 * 15. Cooling
 * 16. GPU_CPU_COMBINED workload (15 minutes) (CPU + GPU)
 * 17. Final cooling
 *
 * GPU load is ONLY ever generated during GPU_CPU_COMBINED. In that
 * mode CPU behaviour is unconstrained while GPU always cycles
 * through: ramping GPU -> chaos GPU -> transition GPU -> repeat.
 *
 * Every run creates:
 *
 * stress_logs/
 * └── <RUN_ID>/
 *     ├── stress_events.jsonl
 *     └── run_metadata.json
 *
 * Nothing from this program is used as an LSTM input feature.
 */
public final class StressTestRunner {

    // ============================================================
    // DEVICE CONFIGURATION
    // ============================================================

    private static final String DEVICE_ID =
            "PRABHSIMRAT";

    /*
     * Suggested values:
     *
     * PRABHSIMRAT -> 101L
     * MANAN       -> 201L
     * SUSHANT     -> 301L
     */
    private static final long SEED = 101L;


    // ============================================================
    // CPU CONFIGURATION
    // ============================================================

    private static final int PROCESSORS =
            Math.max(
                    1,
                    Runtime.getRuntime().availableProcessors()
            );


    // ============================================================
    // OUTPUT DIRECTORY
    // ============================================================

    private static final Path BASE_LOG_DIRECTORY =
            Path.of("stress_logs");


    /*
     * These are assigned once the run ID has been created.
     */
    private static Path runDirectory;

    private static Path eventLogFile;

    private static Path metadataFile;


    // ============================================================
    // SESSION DURATIONS
    // ============================================================

    private static final Duration INITIAL_IDLE =
            Duration.ofMinutes(2);

    private static final Duration RAMP_DURATION =
            Duration.ofMinutes(74);

    private static final Duration CHAOS_DURATION =
            Duration.ofMinutes(74);

    private static final Duration TRANSITION_DURATION =
            Duration.ofMinutes(74);

    private static final Duration MIXED_DURATION =
            Duration.ofMinutes(148);

    /*
     * GPU_CPU_COMBINED runs four times total: after RAMP, after
     * CHAOS, after TRANSITION, and after MIXED.
     */
    private static final Duration GPU_CPU_COMBINED_DURATION_1 =
            Duration.ofMinutes(30);

    private static final Duration GPU_CPU_COMBINED_DURATION_2 =
            Duration.ofMinutes(15);

    private static final Duration GPU_CPU_COMBINED_DURATION_3 =
            Duration.ofMinutes(15);

    private static final Duration GPU_CPU_COMBINED_DURATION_4 =
            Duration.ofMinutes(15);

    private static final Duration COOLING_DURATION =
            Duration.ofMinutes(5);

    private static final Duration FINAL_COOLING_DURATION =
            Duration.ofMinutes(10);


    // ============================================================
    // RAMP PROFILE
    // ============================================================

    private static final List<Integer> RAMP_PROFILE =
            List.of(
                    0, 0,
                    20, 20,
                    40, 40,
                    60, 60,
                    80, 80,
                    100, 100,
                    80, 80,
                    60, 60,
                    40, 40,
                    20, 20,
                    0, 0
            );


    // ============================================================
    // TRANSITION PROFILE
    // ============================================================

    private static final int[][] TRANSITION_PAIRS = {

            {0, 100},
            {100, 0},

            {20, 80},
            {80, 20},

            {40, 90},
            {90, 40},

            {80, 30},
            {30, 80},

            {100, 50},
            {50, 100}
    };


    // ============================================================
    // MIXED MODE CONFIGURATION
    // ============================================================

    private static final int MIXED_RAMP_STEPS = 12;

    private static final int MIXED_CHAOS_STEPS = 12;

    private static final int MIXED_TRANSITION_STEPS = 10;


    private StressTestRunner() {
    }


    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) throws Exception {

        validateConfiguration();

        String runId = createRunId();

        initialiseRunDirectory(runId);

        Random random =
                new Random(SEED);

        AtomicBoolean stopRequested =
                new AtomicBoolean(false);


        // --------------------------------------------------------
        // Initialise GPU stress engine
        // --------------------------------------------------------

        GpuStressEngine gpuEngine =
                new GpuStressEngine();

        if (gpuEngine.isAvailable()) {

            System.out.println(
                    "GPU stress enabled: "
                            + gpuEngine.getDeviceName()
            );

        } else {

            System.out.println(
                    "WARNING: No GPU detected. "
                            + "Running CPU-only stress."
            );
        }


        // --------------------------------------------------------
        // Safe shutdown handling
        // --------------------------------------------------------

        GpuStressEngine gpuRef = gpuEngine;

        Runtime.getRuntime().addShutdownHook(

                new Thread(

                        () -> {

                            stopRequested.set(true);

                            gpuRef.shutdown();

                            try {

                                logInstantEvent(
                                        runId,
                                        "RUN_INTERRUPTED",
                                        "SYSTEM",
                                        0,
                                        -1,
                                        "Program was stopped before normal completion."
                                );

                            } catch (IOException error) {

                                System.err.println(
                                        "Could not record shutdown event: "
                                                + error.getMessage()
                                );
                            }
                        },

                        "stress-shutdown"
                )
        );


        // --------------------------------------------------------
        // Write experiment configuration
        // --------------------------------------------------------

        writeRunMetadata(runId, gpuEngine);

        printExperimentPlan(runId, gpuEngine);


        Instant fullRunStart =
                Instant.now();


        logInstantEvent(
                runId,
                "RUN_START",
                "SYSTEM",
                0,
                0,
                "Automatic stress experiment started."
        );


        boolean completedNormally = false;


        try {

            // ====================================================
            // 1. INITIAL IDLE
            // ====================================================

            runIdleSession(
                    runId,
                    "INITIAL_IDLE",
                    INITIAL_IDLE,
                    stopRequested
            );


            // ====================================================
            // 2. RAMP
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.RAMP,
                    RAMP_DURATION,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 3. COOLING AFTER RAMP
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_RAMP",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 4. GPU_CPU_COMBINED (30 minutes)
            //
            // GPU is only ever active during this mode.
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.GPU_CPU_COMBINED,
                    GPU_CPU_COMBINED_DURATION_1,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 5. COOLING AFTER GPU_CPU_COMBINED (1st)
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_GPU_CPU_COMBINED_1",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 6. CHAOS
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.CHAOS,
                    CHAOS_DURATION,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 7. COOLING AFTER CHAOS
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_CHAOS",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 8. GPU_CPU_COMBINED (15 minutes)
            //
            // GPU is only ever active during this mode.
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.GPU_CPU_COMBINED,
                    GPU_CPU_COMBINED_DURATION_2,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 9. COOLING AFTER GPU_CPU_COMBINED (2nd)
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_GPU_CPU_COMBINED_2",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 10. TRANSITION
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.TRANSITION,
                    TRANSITION_DURATION,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 11. COOLING AFTER TRANSITION
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_TRANSITION",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 12. GPU_CPU_COMBINED (15 minutes)
            //
            // GPU is only ever active during this mode.
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.GPU_CPU_COMBINED,
                    GPU_CPU_COMBINED_DURATION_3,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 13. COOLING AFTER GPU_CPU_COMBINED (3rd)
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_GPU_CPU_COMBINED_3",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 14. MIXED
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.MIXED,
                    MIXED_DURATION,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 15. COOLING AFTER MIXED
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_MIXED",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 16. GPU_CPU_COMBINED (15 minutes)
            //
            // GPU is only ever active during this mode.
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.GPU_CPU_COMBINED,
                    GPU_CPU_COMBINED_DURATION_4,
                    random,
                    stopRequested,
                    gpuEngine
            );


            // ====================================================
            // 17. FINAL COOLING
            // ====================================================

            runIdleSession(
                    runId,
                    "FINAL_COOLING",
                    FINAL_COOLING_DURATION,
                    stopRequested
            );


            if (!stopRequested.get()) {

                completedNormally = true;

            }

        } finally {

            gpuEngine.shutdown();

            Instant fullRunEnd =
                    Instant.now();


            if (completedNormally) {

                logCompletedEvent(
                        runId,
                        "RUN",
                        "COMPLETE_EXPERIMENT",
                        0,
                        0,
                        fullRunStart,
                        fullRunEnd,
                        Duration.between(
                                fullRunStart,
                                fullRunEnd
                        ).toSeconds(),
                        "Experiment completed normally."
                );
            }
        }


        System.out.println();
        System.out.println("======================================");
        System.out.println("DATASET COLLECTION FINISHED");
        System.out.println("======================================");

        System.out.println(
                "Run ID: " + runId
        );

        System.out.println(
                "Finished at: " + Instant.now()
        );

        System.out.println(
                "Logs saved in:"
        );

        System.out.println(
                runDirectory.toAbsolutePath()
        );

        System.out.println("======================================");
    }


    // ============================================================
    // CREATE UNIQUE RUN DIRECTORY
    // ============================================================

    private static void initialiseRunDirectory(
            String runId
    ) throws IOException {


        Files.createDirectories(
                BASE_LOG_DIRECTORY
        );


        runDirectory =
                BASE_LOG_DIRECTORY.resolve(runId);


        /*
         * createDirectory(), not createDirectories().
         *
         * If the exact run folder somehow already exists,
         * the program fails instead of overwriting it.
         */
        Files.createDirectory(
                runDirectory
        );


        eventLogFile =
                runDirectory.resolve(
                        "stress_events.jsonl"
                );


        metadataFile =
                runDirectory.resolve(
                        "run_metadata.json"
                );


        /*
         * Create the empty event log.
         *
         * CREATE_NEW prevents accidental overwriting.
         */
        Files.createFile(
                eventLogFile
        );
    }


    // ============================================================
    // WORKLOAD SESSION
    // ============================================================

    private static void runWorkloadSession(

            String runId,

            WorkloadMode mode,

            Duration sessionDuration,

            Random random,

            AtomicBoolean stopRequested,

            GpuStressEngine gpuEngine

    ) throws Exception {


        System.out.println();
        System.out.println("======================================");

        System.out.println(
                "STARTING SESSION: " + mode
        );

        System.out.println(
                "Duration: "
                        + formatDuration(sessionDuration)
        );

        System.out.println("======================================");


        Instant sessionStart =
                Instant.now();


        Instant finishAt =
                sessionStart.plus(sessionDuration);


        LoadSequence sequence =
                new LoadSequence(
                        mode,
                        random
                );


        int stepNumber = 0;


        while (

                !stopRequested.get()

                        && Instant.now().isBefore(finishAt)

        ) {


            StepLoad stepLoad =
                    sequence.next(stepNumber);

            int load =
                    stepLoad.cpuLoad();

            int gpuLoad =
                    mode == WorkloadMode.GPU_CPU_COMBINED
                            ? stepLoad.gpuLoad()
                            : -1;


            Duration selectedDuration =
                    randomStepDuration(random);


            Duration remaining =
                    Duration.between(
                            Instant.now(),
                            finishAt
                    );


            Duration actualDuration =

                    remaining.compareTo(selectedDuration) < 0

                            ? remaining

                            : selectedDuration;


            if (

                    actualDuration.isNegative()

                            || actualDuration.isZero()

            ) {

                break;
            }


            Instant stepStart =
                    Instant.now();


            if (mode == WorkloadMode.GPU_CPU_COMBINED) {

                System.out.printf(

                        Locale.ROOT,

                        "%s | Step %d | CPU Load %d%% | GPU Load %d%% | Duration %s | Start %s%n",

                        mode,

                        stepNumber + 1,

                        load,

                        gpuLoad,

                        formatDuration(actualDuration),

                        stepStart

                );

            } else {

                System.out.printf(

                        Locale.ROOT,

                        "%s | Step %d | Load %d%% | Duration %s | Start %s%n",

                        mode,

                        stepNumber + 1,

                        load,

                        formatDuration(actualDuration),

                        stepStart

                );
            }


            // ----------------------------------------------------
            // Generate CPU + GPU load in parallel.
            //
            // GPU only ever runs when mode == GPU_CPU_COMBINED.
            // ----------------------------------------------------

            runCpuAndGpuLoad(
                    mode,
                    load,
                    gpuLoad,
                    actualDuration,
                    stopRequested,
                    gpuEngine
            );


            Instant stepEnd =
                    Instant.now();


            // ----------------------------------------------------
            // Log what actually happened
            // ----------------------------------------------------

            logCompletedEvent(

                    runId,

                    "WORKLOAD_STEP",

                    mode.name(),

                    load,

                    gpuLoad,

                    stepNumber + 1,

                    stepStart,

                    stepEnd,

                    Duration.between(
                            stepStart,
                            stepEnd
                    ).toSeconds(),

                    "Completed workload step."
            );


            stepNumber++;
        }


        Instant sessionEnd =
                Instant.now();


        logCompletedEvent(

                runId,

                "SESSION",

                mode.name(),

                0,

                stepNumber,

                sessionStart,

                sessionEnd,

                Duration.between(
                        sessionStart,
                        sessionEnd
                ).toSeconds(),

                "Workload session completed."
        );


        System.out.println(
                "Finished session: " + mode
        );
    }


    // ============================================================
    // IDLE / COOLING SESSION
    // ============================================================

    private static void runIdleSession(

            String runId,

            String sessionName,

            Duration duration,

            AtomicBoolean stopRequested

    ) throws Exception {


        System.out.println();
        System.out.println("======================================");

        System.out.println(
                "STARTING: " + sessionName
        );

        System.out.println(
                "Duration: "
                        + formatDuration(duration)
        );

        System.out.println("======================================");


        Instant startTime =
                Instant.now();


        long endNanos =

                System.nanoTime()

                        + duration.toNanos();


        waitUntil(
                endNanos,
                stopRequested
        );


        Instant endTime =
                Instant.now();


        logCompletedEvent(

                runId,

                "SESSION",

                sessionName,

                0,

                0,

                startTime,

                endTime,

                Duration.between(
                        startTime,
                        endTime
                ).toSeconds(),

                "Idle or cooling session completed."
        );
    }


    // ============================================================
    // CPU + GPU LOAD GENERATION
    // ============================================================

    /**
     * Launches CPU workers and a GPU stress thread in parallel.
     *
     * Both receive the same target load percentage and duration.
     * The method blocks until both have completed.
     */
    private static void runCpuAndGpuLoad(

            WorkloadMode mode,

            int cpuTargetPercent,

            int gpuTargetPercent,

            Duration duration,

            AtomicBoolean stopRequested,

            GpuStressEngine gpuEngine

    ) throws InterruptedException {


        /*
         * GPU is ONLY ever driven in GPU_CPU_COMBINED mode.
         * Every other mode (RAMP, CHAOS, TRANSITION, MIXED) is
         * CPU-only, regardless of whether a GPU is available.
         */
        boolean gpuEnabledForThisStep =

                mode == WorkloadMode.GPU_CPU_COMBINED

                        && gpuEngine.isAvailable();


        /*
         * Start GPU load on a dedicated thread.
         */
        Thread gpuThread = null;

        AtomicBoolean gpuError =
                new AtomicBoolean(false);


        if (gpuEnabledForThisStep) {

            gpuThread = new Thread(

                    () -> {

                        try {

                            gpuEngine.runGpuLoad(
                                    gpuTargetPercent,
                                    duration,
                                    stopRequested
                            );

                        } catch (InterruptedException interrupted) {

                            Thread.currentThread().interrupt();

                        } catch (Exception error) {

                            gpuError.set(true);

                            System.err.println(
                                    "GPU load error: "
                                            + error.getMessage()
                            );
                        }
                    },

                    "gpu-stress-worker"
            );

            gpuThread.setDaemon(true);

            gpuThread.start();
        }


        /*
         * Run CPU load on the calling thread (blocks).
         */
        runCpuLoad(
                cpuTargetPercent,
                duration,
                stopRequested
        );


        /*
         * Wait for GPU thread to finish.
         */
        if (gpuThread != null) {

            gpuThread.join(
                    duration.toMillis() + 5_000
            );
        }
    }


    // ============================================================
    // CPU LOAD GENERATION
    // ============================================================

    private static void runCpuLoad(

            int targetPercent,

            Duration duration,

            AtomicBoolean stopRequested

    ) throws InterruptedException {


        long endNanos =

                System.nanoTime()

                        + duration.toNanos();


        if (targetPercent == 0) {

            waitUntil(
                    endNanos,
                    stopRequested
            );

            return;
        }


        AtomicBoolean workersRunning =
                new AtomicBoolean(true);


        ExecutorService workers =

                Executors.newFixedThreadPool(
                        PROCESSORS
                );


        for (
                int worker = 0;
                worker < PROCESSORS;
                worker++
        ) {

            workers.submit(

                    () -> dutyCycleWorker(

                            targetPercent,

                            endNanos,

                            workersRunning,

                            stopRequested
                    )
            );
        }


        waitUntil(
                endNanos,
                stopRequested
        );


        workersRunning.set(false);


        workers.shutdownNow();


        workers.awaitTermination(
                5,
                TimeUnit.SECONDS
        );
    }


    // ============================================================
    // DUTY CYCLE WORKER
    // ============================================================

    private static void dutyCycleWorker(

            int targetPercent,

            long endNanos,

            AtomicBoolean workersRunning,

            AtomicBoolean stopRequested

    ) {


        long cycleNanos =
                TimeUnit.MILLISECONDS.toNanos(100);


        long busyNanos =

                cycleNanos

                        * targetPercent

                        / 100;


        double value = 0.5;


        while (

                workersRunning.get()

                        && !stopRequested.get()

                        && !Thread.currentThread().isInterrupted()

                        && System.nanoTime() < endNanos

        ) {


            long cycleStart =
                    System.nanoTime();


            long busyUntil =

                    Math.min(

                            endNanos,

                            cycleStart + busyNanos
                    );


            while (

                    System.nanoTime() < busyUntil

                            && workersRunning.get()

                            && !stopRequested.get()

            ) {


                for (
                        int calculation = 0;
                        calculation < 1_000;
                        calculation++
                ) {

                    value =

                            Math.sin(value)

                                    * Math.cos(
                                            value + calculation
                                    )

                                    + 1.000001;
                }
            }


            long elapsed =

                    System.nanoTime()

                            - cycleStart;


            long sleepNanos =

                    cycleNanos

                            - elapsed;


            if (

                    sleepNanos > 0

                            && targetPercent < 100

            ) {

                try {

                    TimeUnit.NANOSECONDS.sleep(
                            sleepNanos
                    );

                } catch (
                        InterruptedException interrupted
                ) {

                    Thread.currentThread().interrupt();

                    return;
                }
            }
        }


        if (value == Double.MIN_VALUE) {

            System.out.print("");

        }
    }


    // ============================================================
    // RANDOM STEP DURATION
    // ============================================================

    private static Duration randomStepDuration(
            Random random
    ) {


        int category =
                random.nextInt(100);


        // 35% chance: 20-45 seconds

        if (category < 35) {

            return Duration.ofSeconds(

                    random.nextLong(
                            20,
                            46
                    )
            );
        }


        // 40% chance: 46-120 seconds

        if (category < 75) {

            return Duration.ofSeconds(

                    random.nextLong(
                            46,
                            121
                    )
            );
        }


        // 25% chance: 121-240 seconds

        return Duration.ofSeconds(

                random.nextLong(
                        121,
                        241
                )
        );
    }


    // ============================================================
    // WAIT
    // ============================================================

    private static void waitUntil(

            long endNanos,

            AtomicBoolean stopRequested

    ) throws InterruptedException {


        while (!stopRequested.get()) {


            long remaining =

                    endNanos

                            - System.nanoTime();


            if (remaining <= 0) {

                return;
            }


            TimeUnit.NANOSECONDS.sleep(

                    Math.min(

                            remaining,

                            TimeUnit.MILLISECONDS
                                    .toNanos(250)
                    )
            );
        }
    }


    // ============================================================
    // LOG COMPLETED EVENT
    // ============================================================

    private static synchronized void logCompletedEvent(

            String runId,

            String eventType,

            String mode,

            int targetLoad,

            int stepNumber,

            Instant startTime,

            Instant endTime,

            long actualDurationSeconds,

            String message

    ) throws IOException {

        /*
         * -1 means "not applicable" (i.e. GPU did not run this
         * step, which is the case for every mode except
         * GPU_CPU_COMBINED).
         */
        logCompletedEvent(

                runId,

                eventType,

                mode,

                targetLoad,

                -1,

                stepNumber,

                startTime,

                endTime,

                actualDurationSeconds,

                message
        );
    }


    private static synchronized void logCompletedEvent(

            String runId,

            String eventType,

            String mode,

            int targetLoad,

            int gpuTargetLoad,

            int stepNumber,

            Instant startTime,

            Instant endTime,

            long actualDurationSeconds,

            String message

    ) throws IOException {


        String jsonLine =

                "{"

                        + "\"eventType\":\""
                        + escapeJson(eventType)
                        + "\","

                        + "\"runId\":\""
                        + escapeJson(runId)
                        + "\","

                        + "\"deviceId\":\""
                        + escapeJson(DEVICE_ID)
                        + "\","

                        + "\"mode\":\""
                        + escapeJson(mode)
                        + "\","

                        + "\"targetLoad\":"
                        + targetLoad
                        + ","

                        + "\"gpuTargetLoad\":"
                        + gpuTargetLoad
                        + ","

                        + "\"stepNumber\":"
                        + stepNumber
                        + ","

                        + "\"startTime\":\""
                        + startTime
                        + "\","

                        + "\"endTime\":\""
                        + endTime
                        + "\","

                        + "\"actualDurationSeconds\":"
                        + actualDurationSeconds
                        + ","

                        + "\"seed\":"
                        + SEED
                        + ","

                        + "\"message\":\""
                        + escapeJson(message)
                        + "\""

                        + "}"

                        + System.lineSeparator();


        Files.writeString(

                eventLogFile,

                jsonLine,

                StandardCharsets.UTF_8,

                StandardOpenOption.APPEND
        );
    }


    // ============================================================
    // LOG INSTANT EVENT
    // ============================================================

    private static synchronized void logInstantEvent(

            String runId,

            String eventType,

            String mode,

            int targetLoad,

            int stepNumber,

            String message

    ) throws IOException {


        Instant timestamp =
                Instant.now();


        logCompletedEvent(

                runId,

                eventType,

                mode,

                targetLoad,

                stepNumber,

                timestamp,

                timestamp,

                0,

                message
        );
    }


    // ============================================================
    // RUN METADATA
    // ============================================================

    private static void writeRunMetadata(
            String runId,
            GpuStressEngine gpuEngine
    ) throws IOException {


        long totalMinutes =

                INITIAL_IDLE.toMinutes()

                        + RAMP_DURATION.toMinutes()

                        + GPU_CPU_COMBINED_DURATION_1.toMinutes()

                        + CHAOS_DURATION.toMinutes()

                        + GPU_CPU_COMBINED_DURATION_2.toMinutes()

                        + TRANSITION_DURATION.toMinutes()

                        + GPU_CPU_COMBINED_DURATION_3.toMinutes()

                        + MIXED_DURATION.toMinutes()

                        + GPU_CPU_COMBINED_DURATION_4.toMinutes()

                        + COOLING_DURATION.toMinutes() * 7

                        + FINAL_COOLING_DURATION.toMinutes();


        String json = """

                {
                  "runId": "%s",
                  "deviceId": "%s",
                  "createdAt": "%s",
                  "seed": %d,
                  "processors": %d,
                  "gpuAvailable": %s,
                  "gpuDeviceName": "%s",

                  "purpose": "Independent CPU + GPU stress verification log",

                  "usedAsModelFeature": false,

                  "durationDistribution": {
                    "short": {
                      "probabilityPercent": 35,
                      "minimumSeconds": 20,
                      "maximumSeconds": 45
                    },
                    "medium": {
                      "probabilityPercent": 40,
                      "minimumSeconds": 46,
                      "maximumSeconds": 120
                    },
                    "long": {
                      "probabilityPercent": 25,
                      "minimumSeconds": 121,
                      "maximumSeconds": 240
                    }
                  },

                  "gpuOnlyMode": "GPU_CPU_COMBINED",

                  "schedule": [
                    {
                      "mode": "INITIAL_IDLE",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "RAMP",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "COOLING_AFTER_RAMP",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "GPU_CPU_COMBINED",
                      "durationMinutes": %d,
                      "gpuEnabled": true,
                      "note": "1st occurrence: runs right after RAMP. GPU cycles ramp -> chaos -> transition; CPU is unconstrained."
                    },
                    {
                      "mode": "COOLING_AFTER_GPU_CPU_COMBINED_1",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "CHAOS",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "COOLING_AFTER_CHAOS",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "GPU_CPU_COMBINED",
                      "durationMinutes": %d,
                      "gpuEnabled": true,
                      "note": "2nd occurrence: runs right after CHAOS. GPU cycles ramp -> chaos -> transition; CPU is unconstrained."
                    },
                    {
                      "mode": "COOLING_AFTER_GPU_CPU_COMBINED_2",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "TRANSITION",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "COOLING_AFTER_TRANSITION",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "GPU_CPU_COMBINED",
                      "durationMinutes": %d,
                      "gpuEnabled": true,
                      "note": "3rd occurrence: runs right after TRANSITION. GPU cycles ramp -> chaos -> transition; CPU is unconstrained."
                    },
                    {
                      "mode": "COOLING_AFTER_GPU_CPU_COMBINED_3",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "MIXED",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "COOLING_AFTER_MIXED",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    },
                    {
                      "mode": "GPU_CPU_COMBINED",
                      "durationMinutes": %d,
                      "gpuEnabled": true,
                      "note": "4th occurrence: runs right after MIXED. GPU cycles ramp -> chaos -> transition; CPU is unconstrained."
                    },
                    {
                      "mode": "FINAL_COOLING",
                      "durationMinutes": %d,
                      "gpuEnabled": false
                    }
                  ],

                  "totalExperimentMinutes": %d
                }
                """.formatted(

                escapeJson(runId),

                escapeJson(DEVICE_ID),

                Instant.now(),

                SEED,

                PROCESSORS,

                gpuEngine.isAvailable(),

                escapeJson(gpuEngine.getDeviceName()),

                INITIAL_IDLE.toMinutes(),

                RAMP_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                GPU_CPU_COMBINED_DURATION_1.toMinutes(),

                COOLING_DURATION.toMinutes(),

                CHAOS_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                GPU_CPU_COMBINED_DURATION_2.toMinutes(),

                COOLING_DURATION.toMinutes(),

                TRANSITION_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                GPU_CPU_COMBINED_DURATION_3.toMinutes(),

                COOLING_DURATION.toMinutes(),

                MIXED_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                GPU_CPU_COMBINED_DURATION_4.toMinutes(),

                FINAL_COOLING_DURATION.toMinutes(),

                totalMinutes
        );


        Files.writeString(

                metadataFile,

                json,

                StandardCharsets.UTF_8,

                StandardOpenOption.CREATE_NEW
        );
    }


    // ============================================================
    // WORKLOAD MODES
    // ============================================================

    private enum WorkloadMode {

        RAMP,

        CHAOS,

        TRANSITION,

        MIXED,

        /*
         * CPU + GPU run together in this mode.
         *
         * CPU behaviour is unconstrained (treated as "anything").
         *
         * GPU always follows a fixed cycle inside this mode:
         * ramping GPU -> chaos GPU -> transition GPU -> repeat.
         *
         * GPU load is ONLY ever generated in this mode. Every other
         * mode (RAMP, CHAOS, TRANSITION, MIXED) is CPU-only.
         */
        GPU_CPU_COMBINED
    }


    // ============================================================
    // LOAD SEQUENCE
    // ============================================================

    /**
     * Holds the load for a single step.
     *
     * cpuLoad and gpuLoad are independent: in GPU_CPU_COMBINED mode
     * they follow completely different sequences. In every other
     * mode gpuLoad simply mirrors cpuLoad, but it is never actually
     * used because GPU never runs outside GPU_CPU_COMBINED.
     */
    private record StepLoad(
            int cpuLoad,
            int gpuLoad
    ) {
    }


    private static final class LoadSequence {


        private final WorkloadMode mode;

        private final Random random;


        private int previousChaosLoad = -1;


        private List<WorkloadMode> mixedBlockOrder =
                new ArrayList<>();


        private int mixedBlockIndex = 0;

        private int mixedStepInBlock = 0;


        // ----------------------------------------------------
        // GPU_CPU_COMBINED state
        // ----------------------------------------------------

        /*
         * Fixed (non-shuffled) GPU cycle for GPU_CPU_COMBINED mode:
         * ramping GPU -> chaos GPU -> transition GPU -> repeat.
         */
        private static final List<WorkloadMode> COMBINED_GPU_BLOCK_ORDER =
                List.of(
                        WorkloadMode.RAMP,
                        WorkloadMode.CHAOS,
                        WorkloadMode.TRANSITION
                );

        private int combinedGpuBlockIndex = 0;

        private int combinedGpuStepInBlock = 0;

        private int previousCombinedGpuChaosLoad = -1;

        private int previousCombinedCpuLoad = -1;


        private LoadSequence(

                WorkloadMode mode,

                Random random

        ) {

            this.mode = mode;

            this.random = random;


            if (mode == WorkloadMode.MIXED) {

                createNewMixedCycle();

            }
        }


        private StepLoad next(
                int stepNumber
        ) {


            return switch (mode) {

                case RAMP ->
                        uniform(rampLoad(stepNumber));

                case CHAOS ->
                        uniform(chaosLoad());

                case TRANSITION ->
                        uniform(transitionLoad(stepNumber));

                case MIXED ->
                        uniform(mixedLoad());

                case GPU_CPU_COMBINED ->
                        combinedLoad();
            };
        }


        /*
         * Used by every CPU-only mode: GPU mirrors CPU here, but
         * since GPU never runs outside GPU_CPU_COMBINED, the
         * gpuLoad value is simply unused.
         */
        private StepLoad uniform(
                int load
        ) {

            return new StepLoad(
                    load,
                    load
            );
        }


        // --------------------------------------------------------
        // RAMP
        // --------------------------------------------------------

        private int rampLoad(
                int stepNumber
        ) {


            return RAMP_PROFILE.get(

                    stepNumber

                            % RAMP_PROFILE.size()
            );
        }


        // --------------------------------------------------------
        // CHAOS
        // --------------------------------------------------------

        private int chaosLoad() {


            int load;


            do {

                load =

                        random.nextInt(11)

                                * 10;

            } while (
                    load == previousChaosLoad
            );


            previousChaosLoad =
                    load;


            return load;
        }


        // --------------------------------------------------------
        // TRANSITION
        // --------------------------------------------------------

        private int transitionLoad(
                int stepNumber
        ) {


            int pairIndex =

                    (stepNumber / 2)

                            % TRANSITION_PAIRS.length;


            int pairPosition =
                    stepNumber % 2;


            return TRANSITION_PAIRS
                    [pairIndex]
                    [pairPosition];
        }


        // --------------------------------------------------------
        // MIXED
        // --------------------------------------------------------

        private int mixedLoad() {


            WorkloadMode activeBlock =

                    mixedBlockOrder.get(
                            mixedBlockIndex
                    );


            int load;


            switch (activeBlock) {

                case RAMP ->

                        load = rampLoad(
                                mixedStepInBlock
                        );


                case CHAOS ->

                        load = chaosLoad();


                case TRANSITION ->

                        load = transitionLoad(
                                mixedStepInBlock
                        );


                default ->

                        throw new IllegalStateException(
                                "Invalid mixed block: "
                                        + activeBlock
                        );
            }


            mixedStepInBlock++;


            int blockLength =

                    switch (activeBlock) {

                        case RAMP ->
                                MIXED_RAMP_STEPS;

                        case CHAOS ->
                                MIXED_CHAOS_STEPS;

                        case TRANSITION ->
                                MIXED_TRANSITION_STEPS;

                        default ->
                                throw new IllegalStateException();
                    };


            if (mixedStepInBlock >= blockLength) {


                mixedStepInBlock = 0;

                mixedBlockIndex++;


                if (
                        mixedBlockIndex
                                >= mixedBlockOrder.size()
                ) {

                    createNewMixedCycle();
                }
            }


            return load;
        }


        // --------------------------------------------------------
        // GPU_CPU_COMBINED
        // --------------------------------------------------------

        /*
         * CPU: "behaviour can be anything" -> unconstrained random
         * load, independent of whatever the GPU is doing.
         *
         * GPU: fixed cycle, always in this order:
         * ramping GPU -> chaos GPU -> transition GPU -> repeat.
         */
        private StepLoad combinedLoad() {


            int cpuLoad =
                    combinedCpuLoad();


            WorkloadMode activeGpuBlock =

                    COMBINED_GPU_BLOCK_ORDER.get(
                            combinedGpuBlockIndex
                    );


            int gpuLoad;


            switch (activeGpuBlock) {

                case RAMP ->

                        gpuLoad = rampLoad(
                                combinedGpuStepInBlock
                        );


                case CHAOS ->

                        gpuLoad = combinedGpuChaosLoad();


                case TRANSITION ->

                        gpuLoad = transitionLoad(
                                combinedGpuStepInBlock
                        );


                default ->

                        throw new IllegalStateException(
                                "Invalid GPU_CPU_COMBINED block: "
                                        + activeGpuBlock
                        );
            }


            combinedGpuStepInBlock++;


            int blockLength =

                    switch (activeGpuBlock) {

                        case RAMP ->
                                MIXED_RAMP_STEPS;

                        case CHAOS ->
                                MIXED_CHAOS_STEPS;

                        case TRANSITION ->
                                MIXED_TRANSITION_STEPS;

                        default ->
                                throw new IllegalStateException();
                    };


            if (combinedGpuStepInBlock >= blockLength) {


                combinedGpuStepInBlock = 0;

                combinedGpuBlockIndex =

                        (combinedGpuBlockIndex + 1)

                                % COMBINED_GPU_BLOCK_ORDER.size();
            }


            return new StepLoad(
                    cpuLoad,
                    gpuLoad
            );
        }


        /*
         * CPU "anything" generator for GPU_CPU_COMBINED. Kept
         * separate from chaosLoad()'s state so CPU randomness never
         * interferes with the GPU's chaos block.
         */
        private int combinedCpuLoad() {


            int load;


            do {

                load =

                        random.nextInt(11)

                                * 10;

            } while (
                    load == previousCombinedCpuLoad
            );


            previousCombinedCpuLoad =
                    load;


            return load;
        }


        /*
         * GPU chaos-block generator for GPU_CPU_COMBINED. Kept
         * separate from chaosLoad()'s state for the same reason.
         */
        private int combinedGpuChaosLoad() {


            int load;


            do {

                load =

                        random.nextInt(11)

                                * 10;

            } while (
                    load == previousCombinedGpuChaosLoad
            );


            previousCombinedGpuChaosLoad =
                    load;


            return load;
        }


        private void createNewMixedCycle() {


            mixedBlockOrder =

                    new ArrayList<>(

                            List.of(

                                    WorkloadMode.RAMP,

                                    WorkloadMode.CHAOS,

                                    WorkloadMode.TRANSITION
                            )
                    );


            Collections.shuffle(

                    mixedBlockOrder,

                    random
            );


            mixedBlockIndex = 0;

            mixedStepInBlock = 0;
        }
    }


    // ============================================================
    // VALIDATION
    // ============================================================

    private static void validateConfiguration() {


        if (
                DEVICE_ID.equals(
                        "CHANGE_THIS_DEVICE_NAME"
                )
        ) {

            throw new IllegalStateException(

                    """

                    DEVICE_ID has not been configured.

                    Change:

                    private static final String DEVICE_ID =
                            "CHANGE_THIS_DEVICE_NAME";

                    to something like:

                    private static final String DEVICE_ID =
                            "PRABHSIMRAT";

                    """
            );
        }
    }


    // ============================================================
    // CREATE RUN ID
    // ============================================================

    private static String createRunId() {


        String safeTimestamp =

                Instant.now()

                        .toString()

                        .replace(":", "-")

                        .replace(".", "-");


        return DEVICE_ID

                + "_"

                + safeTimestamp;
    }


    // ============================================================
    // JSON ESCAPING
    // ============================================================

    private static String escapeJson(
            String value
    ) {


        return value

                .replace("\\", "\\\\")

                .replace("\"", "\\\"")

                .replace("\n", "\\n")

                .replace("\r", "\\r");
    }


    // ============================================================
    // DURATION FORMATTING
    // ============================================================

    private static String formatDuration(
            Duration duration
    ) {


        long seconds =

                Math.max(
                        0,
                        duration.toSeconds()
                );


        return "%02d:%02d:%02d".formatted(

                seconds / 3600,

                seconds % 3600 / 60,

                seconds % 60
        );
    }


    // ============================================================
    // PRINT EXPERIMENT PLAN
    // ============================================================

    private static void printExperimentPlan(
            String runId,
            GpuStressEngine gpuEngine
    ) {


        System.out.println();
        System.out.println("======================================");
        System.out.println("CPU + GPU DATASET EXPERIMENT");
        System.out.println("======================================");

        System.out.println(
                "Run ID: " + runId
        );

        System.out.println(
                "Device: " + DEVICE_ID
        );

        System.out.println(
                "Processors: " + PROCESSORS
        );

        System.out.println(
                "Seed: " + SEED
        );

        System.out.println(
                "GPU: "
                        + (
                                gpuEngine.isAvailable()
                                        ? gpuEngine.getDeviceName()
                                        : "NOT AVAILABLE (CPU-only)"
                        )
        );

        System.out.println();

        System.out.println("Schedule:");

        System.out.println(
                "1.  Initial idle:               2 minutes"
        );

        System.out.println(
                "2.  Ramp (CPU only):           90 minutes"
        );

        System.out.println(
                "3.  Cooling:                    5 minutes"
        );

        System.out.println(
                "4.  GPU+CPU combined:          30 minutes"
        );

        System.out.println(
                "5.  Cooling:                    5 minutes"
        );

        System.out.println(
                "6.  Chaos (CPU only):          90 minutes"
        );

        System.out.println(
                "7.  Cooling:                    5 minutes"
        );

        System.out.println(
                "8.  GPU+CPU combined:          15 minutes"
        );

        System.out.println(
                "9.  Cooling:                    5 minutes"
        );

        System.out.println(
                "10. Transitions (CPU only):    90 minutes"
        );

        System.out.println(
                "11. Cooling:                    5 minutes"
        );

        System.out.println(
                "12. GPU+CPU combined:          15 minutes"
        );

        System.out.println(
                "13. Cooling:                    5 minutes"
        );

        System.out.println(
                "14. Mixed (CPU only):         180 minutes"
        );

        System.out.println(
                "15. Cooling:                    5 minutes"
        );

        System.out.println(
                "16. GPU+CPU combined:          15 minutes"
        );

        System.out.println(
                "17. Final cooling:             10 minutes"
        );

        System.out.println();

        System.out.println(
                "GPU only runs during the GPU+CPU combined sessions (steps 4, 8, 12, and 16)."
        );

        System.out.println();

        System.out.println(
                "Total planned time: 9 hours 32 minutes"
        );

        System.out.println();

        System.out.println(
                "Start the thermal telemetry collector first."
        );

        System.out.println(
                "This stress generator does NOT communicate with the collector."
        );

        System.out.println();

        System.out.println(
                "Stress logs will be saved in:"
        );

        System.out.println(
                runDirectory.toAbsolutePath()
        );

        System.out.println("======================================");
        System.out.println();
    }
}