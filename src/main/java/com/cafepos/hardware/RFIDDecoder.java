package com.cafepos.hardware;

import java.util.HashMap;
import java.util.Map;

/**
 * Décode les UIDs de cartes RFID lus par un lecteur configuré en mode
 * clavier FRANÇAIS (AZERTY), comme le POSLUX RF125.
 *
 * Le lecteur envoie les chiffres de l'UID comme s'il appuyait sur les
 * touches numériques d'un clavier AZERTY, ce qui produit des caractères
 * accentués au lieu de chiffres.
 *
 * Mapping AZERTY touches numériques (sans Shift) → chiffres :
 *   à → 0, & → 1, é → 2, " → 3, ' → 4
 *   ( → 5, - → 6, è → 7, _ → 8, ç → 9
 *
 * La méthode {@link #decode} est idempotente : si l'entrée est déjà
 * composée de chiffres (lecteur QWERTY ou reconfiguré), elle est retournée
 * telle quelle.
 */
public final class RFIDDecoder {

    /** Mapping AZERTY → chiffre (touches numériques, sans Shift). */
    private static final Map<Character, Character> AZERTY_MAP = new HashMap<>(20);

    static {
        AZERTY_MAP.put('\u00E0', '0');  // à → 0
        AZERTY_MAP.put('&',      '1');  // & → 1
        AZERTY_MAP.put('\u00E9', '2');  // é → 2
        AZERTY_MAP.put('"',      '3');  // " → 3
        AZERTY_MAP.put('\'',     '4');  // ' → 4
        AZERTY_MAP.put('(',      '5');  // ( → 5
        AZERTY_MAP.put('-',      '6');  // - → 6
        AZERTY_MAP.put('\u00E8', '7');  // è → 7
        AZERTY_MAP.put('_',      '8');  // _ → 8
        AZERTY_MAP.put('\u00E7', '9');  // ç → 9
        // Variantes majuscules (certains encodages Windows CP1252)
        AZERTY_MAP.put('\u00C0', '0');  // À → 0
        AZERTY_MAP.put('\u00C9', '2');  // É → 2
        AZERTY_MAP.put('\u00C8', '7');  // È → 7
        AZERTY_MAP.put('\u00C7', '9');  // Ç → 9
    }

    private RFIDDecoder() {}

    /**
     * Décode un UID brut reçu en mode clavier AZERTY vers les chiffres réels.
     * <p>
     * Si l'entrée est déjà composée uniquement de chiffres (0-9), elle est
     * retournée inchangée — cela rend la méthode sûre même si le lecteur est
     * reconfiguré en QWERTY ultérieurement.
     *
     * @param rawInput chaîne brute du lecteur
     * @return UID décodé
     */
    public static String decode(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return rawInput;
        }
        // Déjà des chiffres → pas besoin de décodage
        if (rawInput.matches("\\d+")) {
            return rawInput;
        }

        StringBuilder decoded = new StringBuilder(rawInput.length());
        boolean hadAzerty = false;

        for (char c : rawInput.toCharArray()) {
            Character mapped = AZERTY_MAP.get(c);
            if (mapped != null) {
                decoded.append(mapped);
                hadAzerty = true;
            } else {
                decoded.append(c);
            }
        }

        return hadAzerty ? decoded.toString() : rawInput;
    }

    /**
     * Décode et normalise un UID pour stockage et comparaison en base de
     * données :
     * <ol>
     *   <li>Décodage AZERTY si nécessaire</li>
     *   <li>Suppression des espaces et caractères non alphanumériques</li>
     *   <li>Mise en majuscules (pour les UIDs hexadécimaux)</li>
     * </ol>
     *
     * @param rawInput entrée brute du lecteur
     * @return UID normalisé (prêt pour la BDD), ou chaîne vide si null
     */
    public static String normalize(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String decoded = decode(rawInput);
        return decoded.trim()
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase();
    }

    /**
     * Vérifie si une chaîne brute est un UID valide après normalisation.
     * Un UID valide a entre 6 et 20 caractères alphanumériques.
     */
    public static boolean isValidUID(String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String n = normalize(rawInput);
        return n.length() >= 6 && n.length() <= 20;
    }
}
