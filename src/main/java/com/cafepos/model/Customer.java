package com.cafepos.model;

public class Customer {
    private final int id;
    private final String name;
    private final String cardUid;
    private final double balance;
    private final boolean active;

    public Customer(int id, String name, String cardUid, double balance) {
        this(id, name, cardUid, balance, true);
    }

    public Customer(int id, String name, String cardUid, double balance, boolean active) {
        this.id = id;
        this.name = name;
        this.cardUid = cardUid;
        this.balance = balance;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCardUid() {
        return cardUid;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isActive() {
        return active;
    }
}
