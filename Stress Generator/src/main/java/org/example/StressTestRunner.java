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
 * Automatic CPU stress dataset generator for LSTM temperature forecasting.
 *
 * This program is completely independent of the thermal telemetry collector.
 *
 * It generates CPU workload and stores its own verification logs only.
 *
 * One execution automatically performs:
 *
 * 1. Initial idle
 * 2. Ramp workload
 * 3. Cooling
 * 4. Chaos workload
 * 5. Cooling
 * 6. Transition workload
 * 7. Cooling
 * 8. Mixed workload
 * 9. Final cooling
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
            "CHANGE_THIS_DEVICE_NAME";

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
            Duration.ofMinutes(90);

    private static final Duration CHAOS_DURATION =
            Duration.ofMinutes(90);

    private static final Duration TRANSITION_DURATION =
            Duration.ofMinutes(90);

    private static final Duration MIXED_DURATION =
            Duration.ofMinutes(180);

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
        // Safe shutdown handling
        // --------------------------------------------------------

        Runtime.getRuntime().addShutdownHook(

                new Thread(

                        () -> {

                            stopRequested.set(true);

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

        writeRunMetadata(runId);

        printExperimentPlan(runId);


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
                    stopRequested
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
            // 4. CHAOS
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.CHAOS,
                    CHAOS_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // 5. COOLING AFTER CHAOS
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_CHAOS",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 6. TRANSITION
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.TRANSITION,
                    TRANSITION_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // 7. COOLING AFTER TRANSITION
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_TRANSITION",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // 8. MIXED
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.MIXED,
                    MIXED_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // 9. FINAL COOLING
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

            AtomicBoolean stopRequested

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


            int load =
                    sequence.next(stepNumber);


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


            System.out.printf(

                    Locale.ROOT,

                    "%s | Step %d | Load %d%% | Duration %s | Start %s%n",

                    mode,

                    stepNumber + 1,

                    load,

                    formatDuration(actualDuration),

                    stepStart

            );


            // ----------------------------------------------------
            // Generate CPU load
            // ----------------------------------------------------

            runCpuLoad(
                    load,
                    actualDuration,
                    stopRequested
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
            String runId
    ) throws IOException {


        long totalMinutes =

                INITIAL_IDLE.toMinutes()

                        + RAMP_DURATION.toMinutes()

                        + CHAOS_DURATION.toMinutes()

                        + TRANSITION_DURATION.toMinutes()

                        + MIXED_DURATION.toMinutes()

                        + COOLING_DURATION.toMinutes() * 3

                        + FINAL_COOLING_DURATION.toMinutes();


        String json = """

                {
                  "runId": "%s",
                  "deviceId": "%s",
                  "createdAt": "%s",
                  "seed": %d,
                  "processors": %d,

                  "purpose": "Independent CPU stress verification log",

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

                  "schedule": [
                    {
                      "mode": "INITIAL_IDLE",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "RAMP",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "COOLING_AFTER_RAMP",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "CHAOS",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "COOLING_AFTER_CHAOS",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "TRANSITION",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "COOLING_AFTER_TRANSITION",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "MIXED",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "FINAL_COOLING",
                      "durationMinutes": %d
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

                INITIAL_IDLE.toMinutes(),

                RAMP_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                CHAOS_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                TRANSITION_DURATION.toMinutes(),

                COOLING_DURATION.toMinutes(),

                MIXED_DURATION.toMinutes(),

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

        MIXED
    }


    // ============================================================
    // LOAD SEQUENCE
    // ============================================================

    private static final class LoadSequence {


        private final WorkloadMode mode;

        private final Random random;


        private int previousChaosLoad = -1;


        private List<WorkloadMode> mixedBlockOrder =
                new ArrayList<>();


        private int mixedBlockIndex = 0;

        private int mixedStepInBlock = 0;


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


        private int next(
                int stepNumber
        ) {


            return switch (mode) {

                case RAMP ->
                        rampLoad(stepNumber);

                case CHAOS ->
                        chaosLoad();

                case TRANSITION ->
                        transitionLoad(stepNumber);

                case MIXED ->
                        mixedLoad();
            };
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
            String runId
    ) {


        System.out.println();
        System.out.println("======================================");
        System.out.println("CPU DATASET EXPERIMENT");
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

        System.out.println();

        System.out.println("Schedule:");

        System.out.println(
                "1. Initial idle:    2 minutes"
        );

        System.out.println(
                "2. Ramp:           90 minutes"
        );

        System.out.println(
                "3. Cooling:         5 minutes"
        );

        System.out.println(
                "4. Chaos:          90 minutes"
        );

        System.out.println(
                "5. Cooling:         5 minutes"
        );

        System.out.println(
                "6. Transitions:    90 minutes"
        );

        System.out.println(
                "7. Cooling:         5 minutes"
        );

        System.out.println(
                "8. Mixed:         180 minutes"
        );

        System.out.println(
                "9. Final cooling:  10 minutes"
        );

        System.out.println();

        System.out.println(
                "Total planned time: 7 hours 57 minutes"
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