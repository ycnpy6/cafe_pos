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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
