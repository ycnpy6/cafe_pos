package com.cafepos.model;

public enum UnitType {
    UNIT("UNIT", "UNIT", 1.0),
    G("G", "G", 1.0),
    KG("KG", "G", 1000.0),
    ML("ML", "ML", 1.0),
    L("L", "ML", 1000.0);

    private final String displayUnit;
    private final String baseUnit;
    private final double factorToBase;

    UnitType(String displayUnit, String baseUnit, double factorToBase) {
        this.displayUnit = displayUnit;
        this.baseUnit = baseUnit;
        this.factorToBase = factorToBase;
    }

    public String displayUnit() {
        return displayUnit;
    }

    public String baseUnit() {
        return baseUnit;
    }

    public double factorToBase() {
        return factorToBase;
    }

    public static UnitType fromUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return UNIT;
        }
        String normalized = rawUnit.trim().toUpperCase();
        for (UnitType type : values()) {
            if (type.displayUnit.equals(normalized)) {
                return type;
            }
        }
        return UNIT;
    }
}
