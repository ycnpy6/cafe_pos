package com.cafepos.model;

public class Category {
    private final int id;
    private final String name;
    private final String color;
    private final int sortOrder;

    public Category(int id, String name, int sortOrder) {
        this(id, name, null, sortOrder);
    }

    public Category(int id, String name, String color, int sortOrder) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public String toString() {
        return name;
    }
}
