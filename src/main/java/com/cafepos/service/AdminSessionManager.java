package com.cafepos.service;

public final class AdminSessionManager {
    private static boolean adminUnlocked;
    private static long unlockTime;
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L;

    private AdminSessionManager() {
    }

    public static synchronized boolean isAdminUnlocked() {
        if (!adminUnlocked) {
            return false;
        }
        if (System.currentTimeMillis() - unlockTime > SESSION_TIMEOUT_MS) {
            lock();
            return false;
        }
        return true;
    }

    public static synchronized void unlock() {
        adminUnlocked = true;
        unlockTime = System.currentTimeMillis();
    }

    public static synchronized void lock() {
        adminUnlocked = false;
        unlockTime = 0;
    }

    public static synchronized void touch() {
        if (adminUnlocked) {
            unlockTime = System.currentTimeMillis();
        }
    }
}
