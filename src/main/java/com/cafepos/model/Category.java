package com.cafepos.model;

public class Category {
    private final int id;
    private final String name;
    private final String color;
    private final String icon;
    private final int sortOrder;

    public Category(int id, String name, int sortOrder) {
        this(id, name, null, null, sortOrder);
    }

    public Category(int id, String name, String color, int sortOrder) {
        this(id, name, color, null, sortOrder);
    }

    public Category(int id, String name, String color, String icon, int sortOrder) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.icon = icon;
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

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public String toString() {
        return name;
    }
}
