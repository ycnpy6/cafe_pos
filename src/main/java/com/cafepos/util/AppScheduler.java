package com.cafepos.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cafepos.service.PrintQueueService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(AppScheduler.class);
    // Daemon thread so the JVM exits when the JavaFX window closes.
    // Without this, each "closed" app session leaks a live process that
    // accumulates over the day and starves low-end PCs of RAM.
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "app-scheduler");
                t.setDaemon(true);
                return t;
            });
    private static volatile boolean started;

    private AppScheduler() {
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        scheduleDaily(LocalTime.of(23, 55), () -> runEodSafe());
        scheduleDaily(LocalTime.of(0, 5), () -> runBackupSafe());
        scheduleFixedRate(30, TimeUnit.SECONDS, () -> PrintQueueService.getInstance().dispatchAsync());
    }

    private static void scheduleDaily(LocalTime time, Runnable task) {
        long initialDelay = computeDelayMillis(time);
        long period = TimeUnit.DAYS.toMillis(1);
        EXECUTOR.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private static void scheduleFixedRate(long period, TimeUnit unit, Runnable task) {
        long initialDelay = period;
        EXECUTOR.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    private static long computeDelayMillis(LocalTime time) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static void runEodSafe() {
        try {
            new EODService().runEod();
        } catch (Exception ex) {
            LOG.error("EOD echoue", ex);
        }
    }

    private static void runBackupSafe() {
        try {
            new BackupService().runScheduledBackup();
        } catch (Exception ex) {
            LOG.error("Backup echoue", ex);
        }
    }
}
