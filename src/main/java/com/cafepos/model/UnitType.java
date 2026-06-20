package com.cafepos.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog of every unit the POS understands.
 *
 * Each entry knows its display symbol (what the manager picks in the
 * ingredient form), its canonical base unit (G for masses, ML for
 * volumes, UNIT for counts) and its factor to that base.
 *
 * Stock is persisted in BOTH the display unit (for human-friendly tables)
 * and the base unit (used by recipes / sales deduction), so adding a new
 * row here is enough to make the unit available everywhere — no DAO or
 * service change required.
 *
 * Conversion sources:
 *   - 1 oz (avoirdupois) = 28.3495 g
 *   - 1 lb = 453.592 g
 *   - 1 US fl oz = 29.5735 ml
 *   - 1 US tsp = 4.92892 ml, 1 US tbsp = 14.7868 ml
 *   - 1 cup = 240 ml (café-shop standard, 8 fl oz)
 *   - 1 scoop = 60 ml (ice-cream / milkshake convention, ≈ 2 fl oz)
 *   - 1 dozen = 12 units. BOX keeps factor 1 — each ingredient row
 *     declares its own pack contents.
 */
public enum UnitType {
    // ── Count / discrete ─────────────────────────────────────────────────
    UNIT  ("UNIT",  "UNIT", 1.0),
    PIECE ("PIECE", "UNIT", 1.0),
    PACK  ("PACK",  "UNIT", 1.0),
    BOX   ("BOX",   "UNIT", 1.0),
    DOZEN ("DOZEN", "UNIT", 12.0),

    // ── Mass ─────────────────────────────────────────────────────────────
    MG ("MG", "G", 0.001),
    G  ("G",  "G", 1.0),
    KG ("KG", "G", 1000.0),
    OZ ("OZ", "G", 28.3495),
    LB ("LB", "G", 453.592),

    // ── Volume ───────────────────────────────────────────────────────────
    ML   ("ML",    "ML", 1.0),
    CL   ("CL",    "ML", 10.0),
    L    ("L",     "ML", 1000.0),
    FLOZ ("FLOZ",  "ML", 29.5735),
    TSP  ("TSP",   "ML", 4.92892),
    TBSP ("TBSP",  "ML", 14.7868),
    CUP  ("CUP",   "ML", 240.0),
    SCOOP("SCOOP", "ML", 60.0),
    PINT ("PINT",  "ML", 473.176),
    QUART("QUART", "ML", 946.353),
    GAL  ("GAL",   "ML", 3785.41);

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
            case "KILOGRAM", "KILOGRAMME", "KILOGRAMMES", "KILOGRAMS", "KGS" -> "KG";
            case "MILLILITER", "MILLILITRE", "MILLILITERS", "MILLILITRES", "MLS" -> "ML";
            case "MILLIGRAM", "MILLIGRAMME", "MILLIGRAMMES", "MILLIGRAMS", "MGS" -> "MG";
            case "CENTILITRE", "CENTILITER", "CENTILITRES", "CENTILITERS" -> "CL";
            case "PCS", "PC", "PCE", "PIECES" -> "PIECE";
            case "PACKS", "PAQUET", "PAQUETS" -> "PACK";
            case "BOITE", "BOITES", "BOÎTE", "BOÎTES", "BOXES" -> "BOX";
            case "DZ", "DZN", "DOZENS", "DOUZAINE", "DOUZAINES" -> "DOZEN";
            case "OUNCE", "OUNCES", "OZS" -> "OZ";
            case "POUND", "POUNDS", "LBS" -> "LB";
            case "FL OZ", "FL.OZ", "FL_OZ", "FLOUNCE", "FLOUNCES", "FLUID OUNCE", "FLUID OUNCES" -> "FLOZ";
            case "TEASPOON", "TEASPOONS", "C.A.C", "CAC" -> "TSP";
            case "TABLESPOON", "TABLESPOONS", "C.A.S", "CAS" -> "TBSP";
            case "CUPS", "TASSE", "TASSES" -> "CUP";
            case "SCOOPS", "SC", "SCO", "BOULE", "BOULES" -> "SCOOP";
            case "PT", "PINTS" -> "PINT";
            case "QT", "QUARTS" -> "QUART";
            case "GALLON", "GALLONS", "GAL." -> "GAL";
            default -> normalized;
        };
        for (UnitType type : values()) {
            if (type.displayUnit.equals(normalized)) {
                return type;
            }
        }
        return UNIT;
    }

    /** Order chosen so the most-used café units bubble to the top. */
    public static List<String> orderedDisplayUnits() {
        List<String> values = new ArrayList<>();
        // Volume
        values.add(L.displayUnit);
        values.add(CL.displayUnit);
        values.add(ML.displayUnit);
        values.add(FLOZ.displayUnit);
        values.add(CUP.displayUnit);
        values.add(SCOOP.displayUnit);
        values.add(TBSP.displayUnit);
        values.add(TSP.displayUnit);
        values.add(PINT.displayUnit);
        values.add(QUART.displayUnit);
        values.add(GAL.displayUnit);
        // Mass
        values.add(KG.displayUnit);
        values.add(G.displayUnit);
        values.add(MG.displayUnit);
        values.add(LB.displayUnit);
        values.add(OZ.displayUnit);
        // Count
        values.add(UNIT.displayUnit);
        values.add(PIECE.displayUnit);
        values.add(PACK.displayUnit);
        values.add(BOX.displayUnit);
        values.add(DOZEN.displayUnit);
        return values;
    }
}
