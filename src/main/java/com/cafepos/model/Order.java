package com.cafepos.model;

import com.cafepos.util.Money;

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
    private double discountPercent;
    private double discountAmount;
    private double tvaPercent;

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
        discountPercent = 0;
        discountAmount = 0;
    }

    public double getSubtotal() {
        double total = 0;
        for (OrderLine line : lines) {
            total += line.getLineTotal();
        }
        return Money.round2(total);
    }

    public double getAppliedDiscountAmount() {
        double subtotal = getSubtotal();
        if (subtotal <= 0) {
            return 0;
        }
        if (discountPercent > 0) {
            return Money.round2(Math.min(subtotal, subtotal * (discountPercent / 100.0)));
        }
        return Money.round2(Math.min(subtotal, Math.max(0, discountAmount)));
    }

    public double getNetBeforeTva() {
        return Money.round2(Math.max(0, getSubtotal() - getAppliedDiscountAmount()));
    }

    public double getTvaAmount() {
        if (tvaPercent <= 0) {
            return 0;
        }
        return Money.round2(getNetBeforeTva() * (tvaPercent / 100.0));
    }

    public double getTotal() {
        return Money.round2(getNetBeforeTva() + getTvaAmount());
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

    public double getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(double discountPercent) {
        this.discountPercent = Math.max(0, discountPercent);
        if (this.discountPercent > 0) {
            this.discountAmount = 0;
        }
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount = Math.max(0, discountAmount);
        if (this.discountAmount > 0) {
            this.discountPercent = 0;
        }
    }

    public boolean hasDiscount() {
        return getAppliedDiscountAmount() > 0.0001;
    }

    public void clearDiscount() {
        this.discountPercent = 0;
        this.discountAmount = 0;
    }

    public double getTvaPercent() {
        return tvaPercent;
    }

    public void setTvaPercent(double tvaPercent) {
        this.tvaPercent = Math.max(0, tvaPercent);
    }
}
