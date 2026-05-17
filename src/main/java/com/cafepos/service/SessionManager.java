package com.cafepos.service;

import com.cafepos.model.User;

public class SessionManager {
    private static volatile User currentUser;
    private static volatile Integer currentWorkPeriodId;
    private static volatile com.cafepos.model.Order lockedOrder;

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

    public static void setLockedOrder(com.cafepos.model.Order order) {
        lockedOrder = order;
    }

    public static com.cafepos.model.Order consumeLockedOrder() {
        com.cafepos.model.Order order = lockedOrder;
        lockedOrder = null;
        return order;
    }
}
