package com.cafepos.model;

public record PosOrderSummary(
        int orderId,
        String createdAt,
        double total,
        PaymentType paymentType,
        Integer customerId,
        String customerName
) {
}
