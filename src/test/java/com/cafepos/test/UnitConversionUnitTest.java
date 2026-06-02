package com.cafepos.test;

import com.cafepos.model.StockUnit;
import com.cafepos.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("KG", UnitType.orderedDisplayUnits().get(0));
    }
}
