package com.rehnoor.backendserveragent.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StressService {
    private final AtomicInteger targetLoad = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private ExecutorService workers;
    private final List<Future<?>> workerFutures = new ArrayList<>();
    private static final int PROCESSORS = Math.max(1, Runtime.getRuntime().availableProcessors());

    @PostConstruct
    public void start() {
        workers = Executors.newFixedThreadPool(PROCESSORS);
        for (int i = 0; i < PROCESSORS; i++) {
            workerFutures.add(workers.submit(this::resilientDutyCycleWorker));
        }
        System.out.println("StressService started with " + PROCESSORS + " worker threads.");
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        running.set(false);
        workers.shutdownNow();
        workers.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("StressService stopped. Active workers at shutdown: " + activeWorkers.get());
    }

    public void setTargetLoad(int load) {
        if (load < 0 || load > 100) {
            throw new IllegalArgumentException("Target Load must be between 0 and 100");
        }
        targetLoad.set(load);

        int alive = getActiveWorkerCount();
        System.out.println("Target CPU Load = " + load + "% | Active workers: " + alive + "/" + PROCESSORS);

        if (alive < PROCESSORS) {
            System.out.println("WARNING: Only " + alive + " of " + PROCESSORS + " workers alive. Restarting dead workers...");
            restartDeadWorkers();
        }
    }

    public int getTargetLoad() {
        return targetLoad.get();
    }

    public int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    public int getExpectedWorkerCount() {
        return PROCESSORS;
    }

    public boolean isPoolAlive() {
        return workers != null && !workers.isShutdown();
    }

    // ============================================================
    // WORKER LIFECYCLE MANAGEMENT
    // ============================================================

    /**
     * Detects dead workers by checking their Future status and
     * submits replacement workers.
     */
    private synchronized void restartDeadWorkers() {
        if (!running.get() || workers.isShutdown()) {
            return;
        }

        int restarted = 0;
        for (int i = 0; i < workerFutures.size(); i++) {
            Future<?> future = workerFutures.get(i);
            if (future.isDone()) {
                // This worker has exited — replace it
                workerFutures.set(i, workers.submit(this::resilientDutyCycleWorker));
                restarted++;
            }
        }

        // If we still have fewer futures than PROCESSORS, add more
        while (workerFutures.size() < PROCESSORS) {
            workerFutures.add(workers.submit(this::resilientDutyCycleWorker));
            restarted++;
        }

        if (restarted > 0) {
            System.out.println("Restarted " + restarted + " dead worker(s). Active workers now: " + activeWorkers.get());
        }
    }

    // ============================================================
    // RESILIENT DUTY CYCLE WORKER
    // ============================================================

    /**
     * Wraps the duty cycle in error handling so that worker death
     * is always logged and the active worker count is always accurate.
     */
    private void resilientDutyCycleWorker() {
        activeWorkers.incrementAndGet();
        try {
            dutyCycleWorker();
        } catch (Exception e) {
            System.err.println("StressService worker died unexpectedly: " + e.getMessage());
            e.printStackTrace();
        } finally {
            int remaining = activeWorkers.decrementAndGet();
            System.out.println("StressService worker exited. Remaining active workers: " + remaining + "/" + PROCESSORS);
        }
    }

    // ============================================================
    // DUTY CYCLE WORKER
    // ============================================================
    private void dutyCycleWorker() {
        // 100ms cycle
        long cycleNanos = TimeUnit.MILLISECONDS.toNanos(100);

        double value = 0.5;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            int targetPercent = targetLoad.get();
            long busyNanos = cycleNanos * targetPercent / 100;
            long cycleStart = System.nanoTime();

            long busyUntil = cycleStart + busyNanos;

            while (System.nanoTime() < busyUntil) {

                for (int calculation = 0; calculation < 1_000; calculation++) {
                    value = Math.sin(value) * Math.cos(value + calculation) + 1.000001;
                }
            }

            long elapsed = System.nanoTime() - cycleStart;

            long sleepNanos = cycleNanos - elapsed;

            if (sleepNanos > 0 && targetPercent < 100) {
                try {
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);

                } catch (InterruptedException interrupted) {
                    // Don't silently die — log and re-check running flag
                    if (!running.get()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    // If we're still supposed to be running, continue the loop
                    // instead of killing this worker permanently
                    System.out.println("StressService worker interrupted but still running — continuing...");
                }
            }
        }
        if (value == Double.MIN_VALUE) {
            System.out.print("");
        }
    }
}