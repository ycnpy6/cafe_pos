package com.cafepos.model;

public record CashMovementRow(
        int id,
        String createdAt,
        String movementType,
        String category,
        double amount,
        String description,
        String userName
) {
}
