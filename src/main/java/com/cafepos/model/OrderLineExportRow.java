package com.cafepos.model;

public record OrderLineExportRow(
        int orderId,
        String createdAt,
        String productName,
        int quantity,
        double unitPrice,
        double lineTotal,
        String tags,
        String paymentType,
        Integer clientId,
        String clientName,
        String userName
) {
}
