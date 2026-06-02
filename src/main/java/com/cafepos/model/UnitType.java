package com.cafepos.model;

import java.util.ArrayList;
import java.util.List;

public enum UnitType {
    UNIT("UNIT", "UNIT", 1.0),
    PACK("PACK", "UNIT", 1.0),
    PIECE("PIECE", "UNIT", 1.0),
    MG("MG", "G", 0.001),
    G("G", "G", 1.0),
    KG("KG", "G", 1000.0),
    CL("CL", "ML", 10.0),
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
        normalized = switch (normalized) {
            case "LITRE", "LITRES", "LITER", "LITERS" -> "L";
            case "GRAM", "GRAMME", "GRAMMES", "GRAMS" -> "G";
            case "KILOGRAM", "KILOGRAMME", "KILOGRAMMES", "KILOGRAMS" -> "KG";
            case "MILLILITER", "MILLILITRE", "MILLILITERS", "MILLILITRES" -> "ML";
            case "MILLIGRAM", "MILLIGRAMME", "MILLIGRAMMES", "MILLIGRAMS" -> "MG";
            case "PCS", "PC", "PCE" -> "PIECE";
            default -> normalized;
        };
        for (UnitType type : values()) {
            if (type.displayUnit.equals(normalized)) {
                return type;
            }
        }
        return UNIT;
    }

    public static List<String> orderedDisplayUnits() {
        List<String> values = new ArrayList<>();
        values.add(KG.displayUnit);
        values.add(G.displayUnit);
        values.add(MG.displayUnit);
        values.add(L.displayUnit);
        values.add(CL.displayUnit);
        values.add(ML.displayUnit);
        values.add(UNIT.displayUnit);
        values.add(PIECE.displayUnit);
        values.add(PACK.displayUnit);
        return values;
    }
}
