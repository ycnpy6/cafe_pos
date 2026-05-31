package com.cafepos.model;

public record OrderHistoryRow(
        int orderId,
        String createdAt,
        int itemCount,
        double total,
        double ingredientCost,
        double grossProfit,
        PaymentType paymentType,
        Integer clientId,
        String clientName,
        String userName
) {
}
