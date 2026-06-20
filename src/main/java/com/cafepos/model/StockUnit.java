package com.cafepos.model;

public record StockUnit(String unitDisplay, String unitBase, double factorToBase) {
    public static StockUnit fromDisplayUnit(String displayUnit) {
        // Consult the runtime registry so admin-defined unit overrides win
        // over the built-in enum. Falls back to UnitType.UNIT for unknowns.
        UnitRegistry.Entry e = UnitRegistry.resolve(displayUnit);
        return new StockUnit(e.displayUnit(), e.baseUnit(), e.factorToBase());
    }

    public double toBase(double amountInDisplay) {
        return amountInDisplay * (factorToBase <= 0 ? 1.0 : factorToBase);
    }

    public double fromBase(double amountInBase) {
        double factor = factorToBase <= 0 ? 1.0 : factorToBase;
        return amountInBase / factor;
    }
}
