package com.cafepos;

/**
 * Non-Application entry point. Bypasses the JDK's built-in JavaFX launcher
 * detection (which fails in some packaged-runtime scenarios with
 * "Missing JavaFX application class"). Simply delegates to MainApp.main.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
