package com.cafepos.model;

public class Product {
    private final int id;
    private final String name;
    private final double price;
    private final double cost;
    private final int categoryId;
    private final int stock;
    private final boolean active;

    public Product(int id, String name, double price, double cost, int categoryId, int stock, boolean active) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.cost = cost;
        this.categoryId = categoryId;
        this.stock = stock;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public double getCost() {
        return cost;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }
}
