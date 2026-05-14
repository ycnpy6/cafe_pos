package com.cafepos.service;

import com.cafepos.model.User;

public class SessionManager {
    private static volatile User currentUser;
    private static volatile Integer currentWorkPeriodId;

    private SessionManager() {
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentWorkPeriodId(Integer workPeriodId) {
        currentWorkPeriodId = workPeriodId;
    }

    public static Integer getCurrentWorkPeriodId() {
        return currentWorkPeriodId;
    }
}
