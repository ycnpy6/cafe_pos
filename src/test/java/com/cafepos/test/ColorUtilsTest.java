package com.cafepos.test;

import com.cafepos.util.ColorUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilsTest {

    @Test
    void darkBackgroundGetsLightText() {
        assertEquals("#F5ECD7", ColorUtils.contrastTextColor("#6B2D1A")); // brun fonce (Hot Beverages)
        assertEquals("#F5ECD7", ColorUtils.contrastTextColor("#1A4A6B")); // bleu fonce (Cold Beverages)
        assertEquals("#F5ECD7", ColorUtils.contrastTextColor("#000000"));
    }

    @Test
    void lightBackgroundGetsDarkText() {
        assertEquals("#2C1810", ColorUtils.contrastTextColor("#F5ECD7")); // beige clair (fond marque)
        assertEquals("#2C1810", ColorUtils.contrastTextColor("#FFFFFF"));
        assertEquals("#2C1810", ColorUtils.contrastTextColor("#FFEB3B")); // jaune vif
    }

    @Test
    void invalidOrMissingColorFallsBackToDarkText() {
        assertEquals("#2C1810", ColorUtils.contrastTextColor(null));
        assertEquals("#2C1810", ColorUtils.contrastTextColor(""));
        assertEquals("#2C1810", ColorUtils.contrastTextColor("not-a-color"));
    }

    @Test
    void shortHexFormIsSupported() {
        assertEquals("#F5ECD7", ColorUtils.contrastTextColor("#000")); // noir en notation courte
        assertEquals("#2C1810", ColorUtils.contrastTextColor("#FFF"));
    }
}
