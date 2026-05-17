package com.cafepos.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {
    private final List<OrderLine> lines = new ArrayList<>();
    private PaymentType paymentType;
    private Customer customer;
    private final Instant createdAt;
    private double cashAmount;
    private double prepaidAmount;

    public Order() {
        this.createdAt = Instant.now();
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void addLine(OrderLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public void removeLine(OrderLine line) {
        lines.remove(line);
    }

    public void clear() {
        lines.clear();
        customer = null;
        paymentType = null;
        cashAmount = 0;
        prepaidAmount = 0;
    }

    public double getTotal() {
        double total = 0;
        for (OrderLine line : lines) {
            total += line.getLineTotal();
        }
        return total;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public double getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(double cashAmount) {
        this.cashAmount = cashAmount;
    }

    public double getPrepaidAmount() {
        return prepaidAmount;
    }

    public void setPrepaidAmount(double prepaidAmount) {
        this.prepaidAmount = prepaidAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
