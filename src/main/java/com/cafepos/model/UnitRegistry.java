package com.cafepos.model;

import com.cafepos.dao.CustomUnitDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime registry of every unit known to the POS — built-in entries from
 * {@link UnitType} merged with user-defined rows from the {@code custom_units}
 * table.
 *
 * <p>Lookup order in {@link #resolve(String)}: custom override (same
 * displayUnit, active) → built-in enum → fallback {@code UNIT}. This means
 * an admin can change e.g. CUP from 240 ml to 250 ml without touching code.
 *
 * <p>The registry is cached in-memory and only re-read from disk after
 * {@link #refresh()} (called automatically after every CRUD from
 * {@code UnitsManagerDialog}). Existing ingredient rows are not affected:
 * each one snapshots its {@code unit_factor} at insert/update time, so
 * historical conversions stay consistent.
 */
public final class UnitRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(UnitRegistry.class);
    private static final CustomUnitDAO DAO = new CustomUnitDAO();
    private static final AtomicReference<Snapshot> CACHE = new AtomicReference<>(null);

    private UnitRegistry() {}

    public record Entry(String displayUnit,
                        String baseUnit,
                        double factorToBase,
                        CustomUnit.Family family,
                        String label,
                        boolean custom) {
    }

    private record Snapshot(Map<String, Entry> byDisplay, List<Entry> ordered) {}

    /** Force the next lookup to re-read the DB. */
    public static void refresh() {
        CACHE.set(null);
    }

    /**
     * Build the snapshot now so the first call from the JavaFX thread is a
     * pure HashMap lookup. Must be invoked from a background thread (e.g.
     * the {@code db-init} task at startup) to keep the UI responsive.
     */
    public static void prewarm() {
        try {
            snapshot();
        } catch (RuntimeException ex) {
            LOG.warn("UnitRegistry prewarm a echoue, fallback aux unites integrees: {}", ex.getMessage());
        }
    }

    public static Entry resolve(String displayUnit) {
        if (displayUnit == null || displayUnit.isBlank()) {
            return builtinEntry(UnitType.UNIT);
        }
        String key = displayUnit.trim().toUpperCase(Locale.ROOT);
        Entry e = snapshot().byDisplay().get(key);
        if (e != null) return e;
        // Fall through to enum's alias-aware lookup.
        UnitType t = UnitType.fromUnit(displayUnit);
        return builtinEntry(t);
    }

    /** All units, in display order, custom first then built-in. */
    public static List<Entry> all() {
        return Collections.unmodifiableList(snapshot().ordered());
    }

    /** Units belonging to a given family — drives the dialog's combo filter. */
    public static List<String> displayUnitsForFamily(CustomUnit.Family family) {
        List<String> out = new ArrayList<>();
        for (Entry e : snapshot().ordered()) {
            if (e.family() == family) out.add(e.displayUnit());
        }
        return out;
    }

    private static Snapshot snapshot() {
        Snapshot s = CACHE.get();
        if (s != null) return s;
        s = buildSnapshot();
        CACHE.set(s);
        return s;
    }

    private static Snapshot buildSnapshot() {
        Map<String, Entry> byDisplay = new LinkedHashMap<>();
        List<Entry> ordered = new ArrayList<>();

        // 1. Custom rows first — they may override a built-in.
        List<CustomUnit> customs = Collections.emptyList();
        try {
            customs = DAO.findAllActive();
        } catch (Exception ex) {
            LOG.warn("Lecture custom_units impossible (table absente ?): {}", ex.getMessage());
        }
        for (CustomUnit cu : customs) {
            Entry e = new Entry(cu.getDisplayUnit(), cu.getBaseUnit(), cu.getFactorToBase(),
                    cu.getFamily(), cu.getLabel(), true);
            byDisplay.put(cu.getDisplayUnit(), e);
            ordered.add(e);
        }

        // 2. Built-in enum entries, skipping any that were overridden.
        for (String u : UnitType.orderedDisplayUnits()) {
            String key = u.toUpperCase(Locale.ROOT);
            if (byDisplay.containsKey(key)) continue;
            UnitType t = UnitType.fromUnit(u);
            Entry e = new Entry(t.displayUnit(), t.baseUnit(), t.factorToBase(),
                    familyOf(t), null, false);
            byDisplay.put(key, e);
            ordered.add(e);
        }
        return new Snapshot(byDisplay, ordered);
    }

    private static Entry builtinEntry(UnitType t) {
        return new Entry(t.displayUnit(), t.baseUnit(), t.factorToBase(),
                familyOf(t), null, false);
    }

    private static CustomUnit.Family familyOf(UnitType t) {
        return switch (t.baseUnit()) {
            case "ML" -> CustomUnit.Family.LIQUIDE;
            case "G"  -> CustomUnit.Family.SOLIDE;
            default   -> CustomUnit.Family.PIECE;
        };
    }
}
