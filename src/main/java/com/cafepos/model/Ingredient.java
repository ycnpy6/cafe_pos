package com.cafepos.model;

public class Ingredient {
    private final int id;
    private final String name;
    private final String unit;
    private final String unitBase;
    private final double unitFactor;
    private final double packageSize;
    private final double packagePrice;
    private final double stockQuantity;
    private final double minQuantity;
    private final double stockBaseQuantity;
    private final double minBaseQuantity;
    private final boolean active;

    public Ingredient(int id,
                      String name,
                      String unit,
                      double packageSize,
                      double packagePrice,
                      double stockQuantity,
                      double minQuantity,
                      boolean active) {
                this(id, name, unit, unit, 1.0, packageSize, packagePrice, stockQuantity, minQuantity,
                    stockQuantity, minQuantity, active);
                }

                public Ingredient(int id,
                          String name,
                          String unit,
                          String unitBase,
                          double unitFactor,
                          double packageSize,
                          double packagePrice,
                          double stockQuantity,
                          double minQuantity,
                          double stockBaseQuantity,
                          double minBaseQuantity,
                          boolean active) {
        this.id = id;
        this.name = name;
        this.unit = unit;
                this.unitBase = unitBase;
                this.unitFactor = unitFactor <= 0 ? 1.0 : unitFactor;
        this.packageSize = packageSize;
        this.packagePrice = packagePrice;
        this.stockQuantity = stockQuantity;
        this.minQuantity = minQuantity;
                this.stockBaseQuantity = stockBaseQuantity;
                this.minBaseQuantity = minBaseQuantity;
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

    public String getUnitBase() {
        return unitBase;
    }

    public double getUnitFactor() {
        return unitFactor;
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

    public double getStockBaseQuantity() {
        return stockBaseQuantity;
    }

    public double getMinBaseQuantity() {
        return minBaseQuantity;
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
