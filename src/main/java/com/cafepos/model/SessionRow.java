package com.cafepos.model;

public record SessionRow(
        int sessionId,
        String openedAt,
        String closedAt,
        int orderCount,
        double total,
        double cashTotal,
        double prepaidTotal,
        String closeMode
) {
}
