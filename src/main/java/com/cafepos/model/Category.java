package com.cafepos.model;

public class Category {
    private final int id;
    private final String name;
    private final int sortOrder;

    public Category(int id, String name, int sortOrder) {
        this.id = id;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public String toString() {
        return name;
    }
}
