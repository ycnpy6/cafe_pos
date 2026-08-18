package com.cafepos.util;

/**
 * Choix automatique d'une couleur de texte lisible (noir ou blanc) selon la
 * luminance d'un fond donne. Utilise partout ou un texte est pose sur une
 * couleur de fond dynamique (categories personnalisables, tuiles produit) :
 * une couleur de categorie arbitraire choisie en reglages peut etre claire
 * ou sombre, un texte fixe ne serait lisible que dans un cas sur deux.
 */
public final class ColorUtils {
    private static final String DARK_TEXT = "#2C1810";
    private static final String LIGHT_TEXT = "#F5ECD7";

    private ColorUtils() {
    }

    /**
     * @param hexBackground couleur de fond au format #RRGGBB (ou #RGB) ; une
     *                       valeur invalide/absente retombe sur le texte sombre.
     * @return DARK_TEXT ou LIGHT_TEXT, celui offrant le meilleur contraste
     */
    public static String contrastTextColor(String hexBackground) {
        double luminance = relativeLuminance(hexBackground);
        if (luminance < 0) {
            return DARK_TEXT;
        }
        // Seuil ~0.5 sur la luminance perceptuelle (0=noir, 1=blanc) :
        // fond clair -> texte sombre, fond sombre -> texte clair.
        return luminance > 0.5 ? DARK_TEXT : LIGHT_TEXT;
    }

    /** Luminance relative (0-1) selon la formule WCAG ; -1 si la couleur est illisible. */
    public static double relativeLuminance(String hex) {
        int[] rgb = parseHex(hex);
        if (rgb == null) {
            return -1;
        }
        double r = channelLuminance(rgb[0]);
        double g = channelLuminance(rgb[1]);
        double b = channelLuminance(rgb[2]);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelLuminance(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int[] parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            if (value.length() == 3) {
                int r = Integer.parseInt(value.substring(0, 1).repeat(2), 16);
                int g = Integer.parseInt(value.substring(1, 2).repeat(2), 16);
                int b = Integer.parseInt(value.substring(2, 3).repeat(2), 16);
                return new int[]{r, g, b};
            }
            if (value.length() >= 6) {
                int r = Integer.parseInt(value.substring(0, 2), 16);
                int g = Integer.parseInt(value.substring(2, 4), 16);
                int b = Integer.parseInt(value.substring(4, 6), 16);
                return new int[]{r, g, b};
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }
}
