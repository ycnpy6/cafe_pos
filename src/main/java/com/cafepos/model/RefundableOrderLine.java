package com.cafepos.model;

public record RefundableOrderLine(
        int orderLineId,
        int productId,
        String productName,
        int soldQuantity,
        int refundedQuantity,
        int refundableQuantity,
        double unitPrice,
        double lineTotal
) {
}
