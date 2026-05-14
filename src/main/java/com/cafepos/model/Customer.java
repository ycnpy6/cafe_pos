package com.cafepos.model;

public class Customer {
    private final int id;
    private final String name;
    private final String cardUid;
    private final double balance;

    public Customer(int id, String name, String cardUid, double balance) {
        this.id = id;
        this.name = name;
        this.cardUid = cardUid;
        this.balance = balance;
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
}
