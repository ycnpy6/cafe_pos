package com.cafepos.model;

public record RefundLineSelection(
        int orderLineId,
        int productId,
        int quantity,
        double unitPrice
) {
    public double lineTotal() {
        return unitPrice * quantity;
    }
}
