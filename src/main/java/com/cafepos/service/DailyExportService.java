package com.cafepos.service;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.model.CashMovementRow;
import com.cafepos.model.IngredientUsageRow;
import com.cafepos.model.OrderHistoryRow;
import com.cafepos.model.OrderLineExportRow;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.TopItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Export quotidien des analytics dans un dossier date (un sous-dossier par
 * jour, fichiers CSV nommes sur la date). Le dossier racine est le parametre
 * "export.default.dir" : pointez-le vers un dossier Dropbox/OneDrive ou un
 * partage reseau et les rapports se synchronisent automatiquement. Si ce
 * dossier est inaccessible (cle USB absente, reseau coupe), on retombe sur le
 * dossier local %APPDATA%\CafePOS\exports pour ne jamais perdre le rapport.
 */
public class DailyExportService {
    private static final Logger LOG = LoggerFactory.getLogger(DailyExportService.class);
    private static final String EXPORT_DIR_KEY = "export.default.dir";

    // Excel (config FR) attend un CSV separe par des points-virgules avec
    // decimales a virgule; le BOM force la detection UTF-8 des accents.
    private static final String SEP = ";";
    private static final String BOM = "\uFEFF";

    private final ReportService reportService = new ReportService();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    /**
     * Genere les rapports du jour donne dans "<racine>/<yyyy-MM-dd>/".
     * Idempotent : relancer le meme jour ecrase les fichiers avec les donnees
     * a jour (utile pour le rattrapage EOD du lendemain).
     *
     * @return le dossier cree
     */
    public Path exportDay(LocalDate day) throws Exception {
        Path dayDir = resolveExportRoot().resolve(day.toString());
        Files.createDirectories(dayDir);

        SalesSummary summary = reportService.getSummary(day, day);
        writeSummary(dayDir, day, summary);
        writeOrders(dayDir, day, reportService.getOrderHistory(day, day));
        writeOrderLines(dayDir, day, reportService.getOrderLineExports(day, day));
        writeTopProducts(dayDir, day, reportService.getTopItems(day, day, 0x7fffffff));
        writeIngredients(dayDir, day, reportService.getTopIngredientsBySales(day, day, 0));
        writeCashMovements(dayDir, day, reportService.getCashMovements(day, day));

        LOG.info("Export quotidien {} genere dans {}", day, dayDir.toAbsolutePath());
        return dayDir;
    }

    private Path resolveExportRoot() {
        Path fallback = defaultExportDir();
        String configured = null;
        try {
            configured = settingsDAO.getValue(EXPORT_DIR_KEY);
        } catch (Exception ex) {
            LOG.warn("Lecture du dossier export impossible, dossier local utilise", ex);
        }
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            Path root = Paths.get(configured.trim());
            Files.createDirectories(root);
            return root;
        } catch (Exception ex) {
            LOG.warn("Dossier export configure inaccessible ({}), dossier local utilise: {}",
                    configured, fallback, ex);
            return fallback;
        }
    }

    public static Path defaultExportDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".CafePOS", "exports");
        }
        return Paths.get(appData, "CafePOS", "exports");
    }

    private void writeSummary(Path dir, LocalDate day, SalesSummary s) throws IOException {
        StringBuilder sb = header("Indicateur", "Valeur");
        row(sb, "Date", day.toString());
        row(sb, "Chiffre d'affaires total", money(s.total()));
        row(sb, "Nombre de commandes", String.valueOf(s.orderCount()));
        row(sb, "Encaissements especes", money(s.cashTotal()));
        row(sb, "Encaissements prepayes", money(s.prepaidTotal()));
        row(sb, "Cout ingredients", money(s.ingredientCost()));
        row(sb, "Marge brute", money(s.grossProfit()));
        row(sb, "Retraits caisse", money(s.cashWithdrawals()));
        row(sb, "Revenu net", money(s.netRevenue()));
        write(dir, day + "_resume.csv", sb);
    }

    private void writeOrders(Path dir, LocalDate day, List<OrderHistoryRow> rows) throws IOException {
        StringBuilder sb = header("Commande", "Heure", "Articles", "Total", "Cout ingredients",
                "Marge", "Paiement", "Client", "Vendeur");
        for (OrderHistoryRow r : rows) {
            row(sb, String.valueOf(r.orderId()), r.createdAt(), String.valueOf(r.itemCount()),
                    money(r.total()), money(r.ingredientCost()), money(r.grossProfit()),
                    r.paymentType() == null ? "" : r.paymentType().name(),
                    safe(r.clientName()), safe(r.userName()));
        }
        write(dir, day + "_commandes.csv", sb);
    }

    private void writeOrderLines(Path dir, LocalDate day, List<OrderLineExportRow> rows) throws IOException {
        StringBuilder sb = header("Commande", "Heure", "Produit", "Quantite", "Prix unitaire",
                "Total ligne", "Options", "Paiement", "Client", "Vendeur");
        for (OrderLineExportRow r : rows) {
            row(sb, String.valueOf(r.orderId()), r.createdAt(), safe(r.productName()),
                    String.valueOf(r.quantity()), money(r.unitPrice()), money(r.lineTotal()),
                    safe(r.tags()), safe(r.paymentType()), safe(r.clientName()), safe(r.userName()));
        }
        write(dir, day + "_details_commandes.csv", sb);
    }

    private void writeTopProducts(Path dir, LocalDate day, List<TopItem> rows) throws IOException {
        StringBuilder sb = header("Produit", "Quantite vendue", "Chiffre d'affaires");
        for (TopItem r : rows) {
            row(sb, safe(r.name()), String.valueOf(r.quantity()), money(r.revenue()));
        }
        write(dir, day + "_top_produits.csv", sb);
    }

    private void writeIngredients(Path dir, LocalDate day, List<IngredientUsageRow> rows) throws IOException {
        StringBuilder sb = header("Ingredient", "Unite", "Quantite consommee", "Cout total");
        for (IngredientUsageRow r : rows) {
            row(sb, safe(r.name()), safe(r.unit()), quantity(r.quantity()), money(r.totalCost()));
        }
        write(dir, day + "_ingredients_consommes.csv", sb);
    }

    private void writeCashMovements(Path dir, LocalDate day, List<CashMovementRow> rows) throws IOException {
        StringBuilder sb = header("Heure", "Type", "Categorie", "Montant", "Description", "Utilisateur");
        for (CashMovementRow r : rows) {
            row(sb, r.createdAt(), safe(r.movementType()), safe(r.category()),
                    money(r.amount()), safe(r.description()), safe(r.userName()));
        }
        write(dir, day + "_mouvements_caisse.csv", sb);
    }

    private static StringBuilder header(String... columns) {
        StringBuilder sb = new StringBuilder(BOM);
        row(sb, columns);
        return sb;
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(SEP);
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(SEP) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String money(double value) {
        return String.format(Locale.FRENCH, "%.2f", value);
    }

    private static String quantity(double value) {
        return String.format(Locale.FRENCH, "%.3f", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void write(Path dir, String fileName, StringBuilder content) throws IOException {
        Files.writeString(dir.resolve(fileName), content.toString(), StandardCharsets.UTF_8);
    }
}
