package com.cafepos.util;

import com.cafepos.service.SessionManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IdleMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(IdleMonitor.class);
    private static final long TIMEOUT_MS = TimeUnit.HOURS.toMillis(2);
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static volatile long lastActivity = System.currentTimeMillis();
    private static volatile Runnable onTimeout;
    private static volatile boolean started;

    private IdleMonitor() {
    }

    public static void start(Runnable timeoutAction) {
        if (started) {
            return;
        }
        started = true;
        onTimeout = timeoutAction;
        EXECUTOR.scheduleAtFixedRate(IdleMonitor::check, 1, 1, TimeUnit.MINUTES);
    }

    public static void bindScene(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.addEventFilter(InputEvent.ANY, event -> touch());
    }

    private static void touch() {
        lastActivity = System.currentTimeMillis();
    }

    private static void check() {
        if (SessionManager.getCurrentUser() == null) {
            return;
        }
        long idle = System.currentTimeMillis() - lastActivity;
        if (idle < TIMEOUT_MS) {
            return;
        }
        SessionManager.setCurrentUser(null);
        SessionManager.setCurrentWorkPeriodId(null);
        if (onTimeout != null) {
            Platform.runLater(() -> {
                try {
                    onTimeout.run();
                } catch (Exception ex) {
                    LOG.error("Timeout action echouee", ex);
                }
            });
        }
    }
}
