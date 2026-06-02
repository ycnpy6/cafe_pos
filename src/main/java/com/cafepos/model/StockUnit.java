package com.cafepos.model;

public record StockUnit(String unitDisplay, String unitBase, double factorToBase) {
    public static StockUnit fromDisplayUnit(String displayUnit) {
        UnitType type = UnitType.fromUnit(displayUnit);
        return new StockUnit(type.displayUnit(), type.baseUnit(), type.factorToBase());
    }

    public double toBase(double amountInDisplay) {
        return amountInDisplay * (factorToBase <= 0 ? 1.0 : factorToBase);
    }

    public double fromBase(double amountInBase) {
        double factor = factorToBase <= 0 ? 1.0 : factorToBase;
        return amountInBase / factor;
    }
}
