package com.cafepos.util;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.RFIDDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports customers from a CSV file into the customers table.
 *
 * <p>Supports two CSV formats:</p>
 * <ul>
 *   <li><b>Common Grounds format</b> (comma-separated, double-quoted):
 *       {@code "id","name","card_uid","balance","active","phone",...}</li>
 *   <li><b>Legacy POS format</b> (semicolon-separated, double-quoted):
 *       {@code "ID_CLIENT";"REF_CLIENT";...;"NOM";...;"BADGE_CLIENT";...;"SOLDE";...}</li>
 * </ul>
 *
 * <p>Duplicate detection: skips any row whose normalized card_uid already
 * exists in the DB, or whose name+balance matches an existing record with
 * NULL card_uid.</p>
 */
public final class CustomerImporter {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerImporter.class);

    /** Result returned to the caller after an import run. */
    public record ImportResult(int inserted, int skipped, int failed, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
        public String summary() {
            return inserted + " insérés, " + skipped + " ignorés" +
                   (failed > 0 ? ", " + failed + " échecs" : "");
        }
    }

    private CustomerImporter() {}

    // ── Public entry points ────────────────────────────────────────────────

    /** Import from a file on disk. */
    public static ImportResult importFromFile(Path csvPath) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return doImport(reader);
        }
    }

    /** Import from a classpath resource (bundled in the jar). */
    public static ImportResult importFromResource(String resourcePath) throws Exception {
        InputStream is = CustomerImporter.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource introuvable: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return doImport(reader);
        }
    }

    // ── Core logic ─────────────────────────────────────────────────────────

    private static ImportResult doImport(BufferedReader reader) throws Exception {
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0, failed = 0;

        // Read header
        String header = reader.readLine();
        if (header == null) {
            return new ImportResult(0, 0, 0, errors);
        }

        char sep = detectSeparator(header);
        boolean isLegacy = isLegacyFormat(header, sep);

        // Resolve column indices from header
        String[] cols = splitCsvLine(header, sep);
        int idxName     = findCol(cols, isLegacy ? new String[]{"NOM", "RAISON_SOCIALE"} : new String[]{"name"});
        int idxFirst    = findCol(cols, isLegacy ? new String[]{"PRENOM"}                 : new String[]{});
        int idxCard     = findCol(cols, isLegacy ? new String[]{"BADGE_CLIENT"}           : new String[]{"card_uid"});
        int idxBalance  = findCol(cols, isLegacy ? new String[]{"SOLDE"}                  : new String[]{"balance"});
        int idxActive   = findCol(cols, isLegacy ? new String[]{"EST_ACTIVE"}             : new String[]{"active"});
        int idxPhone    = findCol(cols, isLegacy ? new String[]{"PORTABLE", "TEL1"}       : new String[]{"phone"});
        int idxEmail    = findCol(cols, isLegacy ? new String[]{"EMAIL"}                  : new String[]{"email"});
        int idxAddr     = findCol(cols, isLegacy ? new String[]{"ADRESSE_RUE"}            : new String[]{"address"});
        int idxCity     = findCol(cols, isLegacy ? new String[]{"ADRESSE_VILLE"}          : new String[]{});
        int idxSpent    = findCol(cols, isLegacy ? new String[]{"CHIFFRE_AFFAIRE"}        : new String[]{"lifetime_spent"});
        int idxVisits   = findCol(cols, isLegacy ? new String[]{"NBRE_PASSAGE"}           : new String[]{"visit_count"});
        int idxLastVisit= findCol(cols, isLegacy ? new String[]{"DERNIER_DATE_VISITE"}    : new String[]{"last_visit_at"});

        if (idxName < 0) {
            errors.add("Colonne nom introuvable dans l'en-tête");
            return new ImportResult(0, 0, 0, errors);
        }

        CustomerDAO customerDAO = new CustomerDAO();
        AccountTransactionDAO txDAO = new AccountTransactionDAO();

        String line;
        int lineNo = 1;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (line.isBlank()) continue;

            String[] parts = splitCsvLine(line, sep);
            if (parts.length <= idxName) {
                skipped++;
                continue;
            }

            try {
                String rawName  = clean(safeGet(parts, idxName));
                String firstName = clean(safeGet(parts, idxFirst));
                String name = rawName;
                if (isLegacy && !firstName.isBlank() && !rawName.toUpperCase().contains(firstName.toUpperCase())) {
                    name = (rawName + " " + firstName).trim();
                }
                String rawUid = clean(safeGet(parts, idxCard));
                String uid   = RFIDDecoder.normalize(rawUid);
                double balance = parseBalance(safeGet(parts, idxBalance), isLegacy);
                int active   = parseActive(safeGet(parts, idxActive));
                String phone = cleanPhone(safeGet(parts, idxPhone));
                String email = clean(safeGet(parts, idxEmail));
                String street = clean(safeGet(parts, idxAddr));
                String city = clean(safeGet(parts, idxCity));
                String address = (street + (street.isBlank() || city.isBlank() ? "" : ", ") + city).trim();
                Double lifetimeSpent = parseDoubleOrNull(safeGet(parts, idxSpent), isLegacy);
                Integer visits = parseIntOrNull(safeGet(parts, idxVisits));
                String lastVisit = clean(safeGet(parts, idxLastVisit));

                // Skip PASSAGER / blank names / legacy row 0
                if (name.isBlank() || name.equalsIgnoreCase("PASSAGER")) {
                    skipped++;
                    continue;
                }

                // Skip if card UID already exists
                if (!uid.isBlank()) {
                    if (customerDAO.findByCardUid(uid) != null) {
                        skipped++;
                        continue;
                    }
                }

                // Insert customer + optional opening balance transaction + extras
                try (Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    int customerId = customerDAO.insertCustomer(conn, name, uid.isBlank() ? null : uid, balance, active == 1);
                    if (customerId > 0) {
                        customerDAO.updateExtraFields(conn, customerId, phone, email, address,
                                lifetimeSpent, visits, lastVisit);
                        if (balance > 0) {
                            txDAO.insertTransaction(conn, customerId, balance,
                                    "Solde initial (import)", 0, balance, null);
                        }
                        conn.commit();
                        inserted++;
                    } else {
                        conn.rollback();
                        skipped++;
                    }
                }

            } catch (Exception e) {
                failed++;
                errors.add("Ligne " + lineNo + ": " + e.getMessage());
                LOG.warn("Erreur import ligne {}", lineNo, e);
            }
        }

        LOG.info("Import clients: {} insérés, {} ignorés, {} échecs", inserted, skipped, failed);
        return new ImportResult(inserted, skipped, failed, errors);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static char detectSeparator(String header) {
        // Count occurrences of ";" vs "," to decide
        long semis = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        return semis > commas ? ';' : ',';
    }

    private static boolean isLegacyFormat(String header, char sep) {
        String[] cols = splitCsvLine(header, sep);
        for (String col : cols) {
            if (col.equalsIgnoreCase("BADGE_CLIENT") || col.equalsIgnoreCase("ID_CLIENT")) {
                return true;
            }
        }
        return false;
    }

    /** Splits a CSV line respecting double-quoted fields. */
    private static String[] splitCsvLine(String line, char sep) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == sep && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private static int findCol(String[] headers, String[] candidates) {
        if (candidates == null || candidates.length == 0) return -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().replace("\"", "").toUpperCase();
            for (String candidate : candidates) {
                if (h.equals(candidate.toUpperCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String safeGet(String[] parts, int idx) {
        if (idx < 0 || idx >= parts.length) return "";
        return parts[idx];
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        // Remove surrounding quotes if present
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1);
        }
        s = s.trim();
        if (s.equalsIgnoreCase("NULL")) return "";
        return s;
    }

    /**
     * For legacy DB, balances are stored in millimes (×1000).
     * E.g. 25000 millimes = 25.00 DZD.
     * For the new DB format they are already in DZD (REAL).
     */
    private static double parseBalance(String raw, boolean isLegacy) {
        String s = clean(raw);
        if (s.isBlank()) return 0;
        try {
            double val = Double.parseDouble(s);
            return isLegacy ? val / 1000.0 : val;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseActive(String raw) {
        String s = clean(raw);
        if (s.isBlank()) return 1;
        try {
            return Integer.parseInt(s) == 1 ? 1 : 0;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String cleanPhone(String raw) {
        String s = clean(raw);
        if (s.equals("0")) return "";
        // Keep only digits and +
        s = s.replaceAll("[^0-9+]", "");
        // Discard placeholder "0" single digit
        return s.length() <= 1 ? "" : s;
    }

    private static Double parseDoubleOrNull(String raw, boolean isLegacy) {
        String s = clean(raw);
        if (s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s);
            return isLegacy ? v / 1000.0 : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String raw) {
        String s = clean(raw);
        if (s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            try {
                return (int) Math.round(Double.parseDouble(s));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
}
