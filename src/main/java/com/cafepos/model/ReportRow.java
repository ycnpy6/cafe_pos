package com.cafepos.model;

public class ReportRow {
    private final int orderId;
    private final String createdAt;
    private final PaymentType paymentType;
    private final double total;

    public ReportRow(int orderId, String createdAt, PaymentType paymentType, double total) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.paymentType = paymentType;
        this.total = total;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public double getTotal() {
        return total;
    }
}
