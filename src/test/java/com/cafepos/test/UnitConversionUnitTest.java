package com.cafepos.test;

import com.cafepos.model.StockUnit;
import com.cafepos.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnitConversionUnitTest {
    @Test
    void liquidUnitsConvertToBase() {
        StockUnit litre = StockUnit.fromDisplayUnit("L");
        StockUnit ml = StockUnit.fromDisplayUnit("ML");
        assertEquals("ML", litre.unitBase());
        assertEquals(1000.0, litre.factorToBase(), 0.0001);
        assertEquals(1.0, ml.factorToBase(), 0.0001);
    }

    @Test
    void weightUnitsConvertToBase() {
        StockUnit kg = StockUnit.fromDisplayUnit("KG");
        StockUnit g = StockUnit.fromDisplayUnit("G");
        assertEquals("G", kg.unitBase());
        assertEquals(1000.0, kg.factorToBase(), 0.0001);
        assertEquals(1.0, g.factorToBase(), 0.0001);
    }

    @Test
    void orderedDisplayUnitsContainsBaseTypes() {
        java.util.List<String> units = UnitType.orderedDisplayUnits();
        // Order is curated for the café UI; here we only assert that all
        // canonical units are exposed to the dialog (the exact ordering
        // is a UX concern and may evolve).
        for (String expected : new String[] {
                "KG", "G", "MG", "LB", "OZ",
                "L", "CL", "ML", "FLOZ", "CUP", "SCOOP", "TBSP", "TSP", "PINT", "QUART", "GAL",
                "UNIT", "PIECE", "PACK", "BOX", "DOZEN"
        }) {
            assertTrue(units.contains(expected), "expected unit " + expected + " to be exposed");
        }
    }

    @Test
    void newUnitsHaveExpectedConversions() {
        assertEquals(28.3495, StockUnit.fromDisplayUnit("OZ").factorToBase(), 0.0001);
        assertEquals(453.592, StockUnit.fromDisplayUnit("LB").factorToBase(), 0.001);
        assertEquals(29.5735, StockUnit.fromDisplayUnit("FLOZ").factorToBase(), 0.0001);
        assertEquals(4.92892, StockUnit.fromDisplayUnit("TSP").factorToBase(), 0.0001);
        assertEquals(14.7868, StockUnit.fromDisplayUnit("TBSP").factorToBase(), 0.0001);
        assertEquals(240.0,   StockUnit.fromDisplayUnit("CUP").factorToBase(), 0.0001);
        assertEquals(60.0,    StockUnit.fromDisplayUnit("SCOOP").factorToBase(), 0.0001);
        assertEquals(12.0,    StockUnit.fromDisplayUnit("DOZEN").factorToBase(), 0.0001);
        // base units derived correctly
        assertEquals("G",    StockUnit.fromDisplayUnit("OZ").unitBase());
        assertEquals("ML",   StockUnit.fromDisplayUnit("FLOZ").unitBase());
        assertEquals("UNIT", StockUnit.fromDisplayUnit("DOZEN").unitBase());
    }

    @Test
    void aliasesNormaliseToCanonical() {
        assertEquals("OZ",   UnitType.fromUnit("ounce").displayUnit());
        assertEquals("FLOZ", UnitType.fromUnit("fl oz").displayUnit());
        assertEquals("TBSP", UnitType.fromUnit("Tablespoon").displayUnit());
        assertEquals("CUP",  UnitType.fromUnit("tasse").displayUnit());
        assertEquals("SCOOP",UnitType.fromUnit("boules").displayUnit());
        assertEquals("LB",   UnitType.fromUnit("pounds").displayUnit());
    }
}
