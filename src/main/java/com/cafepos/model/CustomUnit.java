package com.cafepos.model;

/**
 * Runtime-defined unit of measurement. Stored in the {@code custom_units}
 * table. When {@code displayUnit} matches a built-in {@link UnitType}
 * entry, this override takes precedence (e.g. allows changing CUP from
 * 240 ml to a regional 250 ml without recompiling).
 */
public class CustomUnit {
    public enum Family { LIQUIDE, SOLIDE, PIECE }

    private final int id;
    private final String displayUnit;
    private final String baseUnit;
    private final double factorToBase;
    private final Family family;
    private final String label;
    private final boolean active;

    public CustomUnit(int id,
                      String displayUnit,
                      String baseUnit,
                      double factorToBase,
                      Family family,
                      String label,
                      boolean active) {
        this.id = id;
        this.displayUnit = displayUnit == null ? "" : displayUnit.trim().toUpperCase();
        this.baseUnit = baseUnit == null ? "" : baseUnit.trim().toUpperCase();
        this.factorToBase = factorToBase <= 0 ? 1.0 : factorToBase;
        this.family = family == null ? Family.PIECE : family;
        this.label = label;
        this.active = active;
    }

    public int getId() { return id; }
    public String getDisplayUnit() { return displayUnit; }
    public String getBaseUnit() { return baseUnit; }
    public double getFactorToBase() { return factorToBase; }
    public Family getFamily() { return family; }
    public String getLabel() { return label; }
    public boolean isActive() { return active; }
}
