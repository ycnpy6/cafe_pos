package com.cafepos.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static String sha256Hex(String input) {
        try {
            // Hachage SHA-256 pour ne jamais stocker le PIN en clair.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
