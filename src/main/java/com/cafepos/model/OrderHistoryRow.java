package com.cafepos.model;

public record OrderHistoryRow(
        int orderId,
        String createdAt,
        int itemCount,
        double total,
        PaymentType paymentType,
        String userName
) {
}
