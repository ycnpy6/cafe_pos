package com.cafepos.model;

public class Ingredient {
    private final int id;
    private final String name;
    private final String unit;
    private final double packageSize;
    private final double packagePrice;
    private final double stockQuantity;
    private final double minQuantity;
    private final boolean active;

    public Ingredient(int id,
                      String name,
                      String unit,
                      double packageSize,
                      double packagePrice,
                      double stockQuantity,
                      double minQuantity,
                      boolean active) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.packageSize = packageSize;
        this.packagePrice = packagePrice;
        this.stockQuantity = stockQuantity;
        this.minQuantity = minQuantity;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public double getPackageSize() {
        return packageSize;
    }

    public double getPackagePrice() {
        return packagePrice;
    }

    public double getStockQuantity() {
        return stockQuantity;
    }

    public double getMinQuantity() {
        return minQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public double getUnitCost() {
        if (packageSize <= 0) {
            return 0;
        }
        return packagePrice / packageSize;
    }
}
