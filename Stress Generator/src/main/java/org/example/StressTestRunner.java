package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * One execution automatically performs:
 *
 * 1. Warm-up idle
 * 2. Ramp workload
 * 3. Cooling
 * 4. Chaos workload
 * 5. Cooling
 * 6. Transition workload
 * 7. Cooling
 * 8. Mixed workload
 * 9. Final cooling
 *
 * Files generated:
 *
 * current_tier.json
 *     Current workload state for the telemetry collector.
 *
 * stress_events.jsonl
 *     Permanent workload-event history.
 *
 * run_metadata.json
 *     Complete description of the experiment.
 */
public final class StressTestRunner {

    // ============================================================
    // GENERAL CONFIGURATION
    // ============================================================

    private static final int PROCESSORS =
            Math.max(
                    1,
                    Runtime.getRuntime().availableProcessors()
            );

    /*
     * Change this manually for each laptop.
     *
     * Examples:
     *
     * PRABHSIMRAT
     * MANAN
     * SUSHANT
     */
    private static final String DEVICE_ID =
            "CHANGE_THIS_DEVICE_NAME";


    // ============================================================
    // OUTPUT FILES
    // ============================================================

    private static final Path CURRENT_STATE_FILE =
            Path.of("current_tier.json");

    private static final Path EVENT_LOG_FILE =
            Path.of("stress_events.jsonl");

    private static final Path RUN_METADATA_FILE =
            Path.of("run_metadata.json");


    // ============================================================
    // RANDOM SEED
    // ============================================================

    /*
     * Keep this fixed if you want the same experiment again.
     *
     * Use different seeds on different laptops for training data.
     *
     * Example:
     *
     * Prabhsimrat = 101
     * Manan       = 201
     * Sushant     = 301
     */
    private static final long SEED = 101L;


    // ============================================================
    // AUTOMATIC SESSION DURATIONS
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
    // WORKLOAD PROFILES
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


    /*
     * These transitions are intentionally more varied than only
     * switching between 0% and 100%.
     */
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

        Random random = new Random(SEED);

        AtomicBoolean stopRequested =
                new AtomicBoolean(false);


        // --------------------------------------------------------
        // Shutdown handling
        // --------------------------------------------------------

        Runtime.getRuntime().addShutdownHook(

                new Thread(

                        () -> {

                            stopRequested.set(true);

                            try {

                                writeCurrentState(
                                        runId,
                                        "STOPPED",
                                        0,
                                        -1,
                                        "SHUTDOWN"
                                );

                            } catch (IOException error) {

                                System.err.println(
                                        "Could not reset workload state: "
                                                + error.getMessage()
                                );
                            }
                        },

                        "stress-shutdown"
                )
        );


        // --------------------------------------------------------
        // Prepare output files
        // --------------------------------------------------------

        initialiseEventLog();

        writeRunMetadata(runId);


        printExperimentPlan(runId);


        try {

            // ====================================================
            // SESSION 1: INITIAL IDLE
            // ====================================================

            runIdleSession(
                    runId,
                    "INITIAL_IDLE",
                    INITIAL_IDLE,
                    stopRequested
            );


            // ====================================================
            // SESSION 2: RAMP
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.RAMP,
                    RAMP_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // COOLING
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_RAMP",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // SESSION 3: CHAOS
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.CHAOS,
                    CHAOS_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // COOLING
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_CHAOS",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // SESSION 4: TRANSITIONS
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.TRANSITION,
                    TRANSITION_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // COOLING
            // ====================================================

            runIdleSession(
                    runId,
                    "COOLING_AFTER_TRANSITION",
                    COOLING_DURATION,
                    stopRequested
            );


            // ====================================================
            // SESSION 5: MIXED
            // ====================================================

            runWorkloadSession(
                    runId,
                    WorkloadMode.MIXED,
                    MIXED_DURATION,
                    random,
                    stopRequested
            );


            // ====================================================
            // FINAL COOLING
            // ====================================================

            runIdleSession(
                    runId,
                    "FINAL_COOLING",
                    FINAL_COOLING_DURATION,
                    stopRequested
            );

        } finally {

            writeCurrentState(
                    runId,
                    "FINISHED",
                    0,
                    -1,
                    "FINISHED"
            );

            logEvent(
                    runId,
                    "RUN_FINISHED",
                    0,
                    -1,
                    0
            );
        }


        System.out.println();
        System.out.println("======================================");
        System.out.println("DATASET COLLECTION FINISHED");
        System.out.println("======================================");

        System.out.println("Run ID: " + runId);

        System.out.println(
                "Finished at: " + Instant.now()
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


        Instant finishAt =
                Instant.now().plus(sessionDuration);


        LoadSequence sequence =
                new LoadSequence(mode, random);


        int stepNumber = 0;


        logEvent(
                runId,
                mode.name() + "_SESSION_START",
                0,
                stepNumber,
                sessionDuration.toSeconds()
        );


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


            // ----------------------------------------------------
            // Write workload state
            // ----------------------------------------------------

            writeCurrentState(

                    runId,

                    mode.name(),

                    load,

                    stepNumber + 1,

                    "RUNNING"
            );


            // ----------------------------------------------------
            // Permanent event history
            // ----------------------------------------------------

            logEvent(

                    runId,

                    mode.name(),

                    load,

                    stepNumber + 1,

                    actualDuration.toSeconds()
            );


            System.out.printf(

                    Locale.ROOT,

                    "%s | Step %d | Load %d%% | Duration %s%n",

                    mode,

                    stepNumber + 1,

                    load,

                    formatDuration(actualDuration)

            );


            // ----------------------------------------------------
            // Generate CPU load
            // ----------------------------------------------------

            runCpuLoad(

                    load,

                    actualDuration,

                    stopRequested

            );


            stepNumber++;
        }


        logEvent(

                runId,

                mode.name() + "_SESSION_END",

                0,

                stepNumber,

                0
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
                "Duration: " + formatDuration(duration)
        );

        System.out.println("======================================");


        writeCurrentState(

                runId,

                sessionName,

                0,

                0,

                "IDLE"
        );


        logEvent(

                runId,

                sessionName,

                0,

                0,

                duration.toSeconds()
        );


        long endNanos =

                System.nanoTime()

                        + duration.toNanos();


        waitUntil(

                endNanos,

                stopRequested

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


        /*
         * 100 ms duty-cycle window.
         *
         * Example:
         *
         * 60% target:
         *
         * busy  = 60 ms
         * sleep = 40 ms
         */
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


        /*
         * Prevent theoretical elimination of calculations.
         */
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


        // --------------------------------------------------------
        // 35% SHORT EVENTS
        // 20-45 seconds
        // --------------------------------------------------------

        if (category < 35) {

            return Duration.ofSeconds(

                    random.nextLong(
                            20,
                            46
                    )

            );
        }


        // --------------------------------------------------------
        // 40% MEDIUM EVENTS
        // 46-120 seconds
        // --------------------------------------------------------

        if (category < 75) {

            return Duration.ofSeconds(

                    random.nextLong(
                            46,
                            121
                    )

            );
        }


        // --------------------------------------------------------
        // 25% LONG EVENTS
        // 121-240 seconds
        // --------------------------------------------------------

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
    // CURRENT STATE JSON
    // ============================================================

    private static void writeCurrentState(

            String runId,

            String mode,

            int targetLoad,

            int stepNumber,

            String status

    ) throws IOException {


        String json = """

                {
                  "timestamp": "%s",
                  "runId": "%s",
                  "deviceId": "%s",
                  "mode": "%s",
                  "targetLoad": %d,
                  "stepNumber": %d,
                  "status": "%s",
                  "seed": %d
                }
                """.formatted(

                Instant.now(),

                escapeJson(runId),

                escapeJson(DEVICE_ID),

                escapeJson(mode),

                targetLoad,

                stepNumber,

                escapeJson(status),

                SEED

        );


        atomicWrite(

                CURRENT_STATE_FILE,

                json

        );
    }


    // ============================================================
    // EVENT LOG
    // ============================================================

    private static synchronized void logEvent(

            String runId,

            String mode,

            int targetLoad,

            int stepNumber,

            long durationSeconds

    ) throws IOException {


        String jsonLine =

                "{"

                        + "\"timestamp\":\""
                        + Instant.now()
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

                        + "\"durationSeconds\":"
                        + durationSeconds
                        + ","

                        + "\"seed\":"
                        + SEED

                        + "}"
                        + System.lineSeparator();


        Files.writeString(

                EVENT_LOG_FILE,

                jsonLine,

                StandardCharsets.UTF_8,

                StandardOpenOption.CREATE,

                StandardOpenOption.APPEND

        );
    }


    // ============================================================
    // RUN METADATA JSON
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

                  "recommendedTelemetryIntervalSeconds": 2,

                  "modelExperiment": {
                    "lookbackRows": 60,
                    "historySeconds": 120,
                    "predictionHorizonRows": 30,
                    "predictionHorizonSeconds": 60
                  },

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
                      "mode": "COOLING",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "CHAOS",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "COOLING",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "TRANSITION",
                      "durationMinutes": %d
                    },
                    {
                      "mode": "COOLING",
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


        atomicWrite(

                RUN_METADATA_FILE,

                json

        );
    }


    // ============================================================
    // INITIALISE EVENT LOG
    // ============================================================

    private static void initialiseEventLog()

            throws IOException {


        /*
         * Delete the previous event file.
         *
         * This ensures every run gets a clean event history.
         */
        Files.deleteIfExists(

                EVENT_LOG_FILE

        );
    }


    // ============================================================
    // ATOMIC FILE WRITE
    // ============================================================

    private static void atomicWrite(

            Path target,

            String content

    ) throws IOException {


        Path absoluteTarget =
                target.toAbsolutePath();


        Path parent =
                absoluteTarget.getParent();


        if (parent != null) {

            Files.createDirectories(parent);

        }


        Path temporary =

                Files.createTempFile(

                        parent,

                        "stress-state-",

                        ".tmp"

                );


        Files.writeString(

                temporary,

                content,

                StandardCharsets.UTF_8

        );


        try {

            Files.move(

                    temporary,

                    absoluteTarget,

                    StandardCopyOption.REPLACE_EXISTING,

                    StandardCopyOption.ATOMIC_MOVE

            );

        } catch (

                java.nio.file.AtomicMoveNotSupportedException unsupported

        ) {

            Files.move(

                    temporary,

                    absoluteTarget,

                    StandardCopyOption.REPLACE_EXISTING

            );
        }
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


        /*
         * Mixed mode uses blocks.
         *
         * The block order is shuffled every cycle.
         */
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


            previousChaosLoad = load;


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


            if (

                    mixedStepInBlock >= blockLength

            ) {


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
    // RUN ID
    // ============================================================

    private static String createRunId() {


        return DEVICE_ID

                + "_"

                + Instant.now()

                .toString()

                .replace(":", "-");
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

        System.out.println(
                "Schedule:"
        );

        System.out.println(
                "1. Initial idle:   2 minutes"
        );

        System.out.println(
                "2. Ramp:          90 minutes"
        );

        System.out.println(
                "3. Cooling:        5 minutes"
        );

        System.out.println(
                "4. Chaos:         90 minutes"
        );

        System.out.println(
                "5. Cooling:        5 minutes"
        );

        System.out.println(
                "6. Transitions:   90 minutes"
        );

        System.out.println(
                "7. Cooling:        5 minutes"
        );

        System.out.println(
                "8. Mixed:        180 minutes"
        );

        System.out.println(
                "9. Final cooling: 10 minutes"
        );

        System.out.println();

        System.out.println(
                "Start the telemetry collector BEFORE this program."
        );

        System.out.println("======================================");
        System.out.println();
    }
}