package com.cafepos.model;

public record AccountTransaction(
        int id,
        int customerId,
        double amount,
        String description,
        String createdAt,
        double balanceAfter,
        Integer orderId
) {
}
