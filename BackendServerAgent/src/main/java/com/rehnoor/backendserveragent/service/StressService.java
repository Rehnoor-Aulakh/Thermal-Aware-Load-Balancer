package com.rehnoor.backendserveragent.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StressService {
    private final AtomicInteger targetLoad = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ExecutorService workers;
    private static final int PROCESSORS = Math.max( 1, Runtime.getRuntime().availableProcessors());

    @PostConstruct
    public void start(){
        workers = Executors.newFixedThreadPool(PROCESSORS);
        for(int i=0; i<PROCESSORS; i++){
            workers.submit(this::dutyCycleWorker);
        }
        System.out.println("StressService started.");
    }

    @PreDestroy
    public void stop() throws InterruptedException{
        running.set(false);
        workers.shutdownNow();
        workers.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("StressService stopped.");
    }

    public void setTargetLoad(int load){
        if(load<0 || load>100){
            throw new IllegalArgumentException("Target Load must be between 0 and 100");
        }
        targetLoad.set(load);
        System.out.println("Target CPU Load = " + load + "%");
    }

    public int getTargetLoad(){
        return targetLoad.get();
    }

    // ============================================================
    // DUTY CYCLE WORKER
    // ============================================================
    private void dutyCycleWorker() {
        // 100ms cycle
        long cycleNanos
                = TimeUnit.MILLISECONDS.toNanos(100);

        double value = 0.5;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            int targetPercent = targetLoad.get();
            long busyNanos = cycleNanos * targetPercent /100;
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

                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (value == Double.MIN_VALUE) {
            System.out.print("");
        }
    }
}