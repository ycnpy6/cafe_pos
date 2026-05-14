package com.cafepos.model;

public class Tag {
    private final int id;
    private final int groupId;
    private final String name;
    private final double priceModifier;

    public Tag(int id, int groupId, String name, double priceModifier) {
        this.id = id;
        this.groupId = groupId;
        this.name = name;
        this.priceModifier = priceModifier;
    }

    public int getId() {
        return id;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public double getPriceModifier() {
        return priceModifier;
    }
}
