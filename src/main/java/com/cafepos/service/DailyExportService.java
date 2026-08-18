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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Export quotidien des analytics : un classeur Excel stylise (tables aux
 * couleurs de la marque) par jour, nomme sur la date, dans un sous-dossier
 * date. Le dossier racine est le parametre "export.default.dir" : pointez-le
 * vers un dossier Dropbox/OneDrive ou un partage reseau et les rapports se
 * synchronisent automatiquement. S'il est inaccessible (cle USB absente,
 * reseau coupe), on retombe sur %APPDATA%\CafePOS\exports pour ne jamais
 * perdre le rapport. Le fichier .xls contient du HTML : Excel l'ouvre
 * nativement en conservant les styles.
 */
public class DailyExportService {
    private static final Logger LOG = LoggerFactory.getLogger(DailyExportService.class);
    private static final String EXPORT_DIR_KEY = "export.default.dir";

    private static final DateTimeFormatter DAY_TITLE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    private final ReportService reportService = new ReportService();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    /**
     * Genere le rapport du jour donne dans "<racine>/<yyyy-MM-dd>/".
     * Idempotent : relancer le meme jour ecrase le fichier avec les donnees
     * a jour (utile pour le rattrapage EOD du lendemain).
     *
     * @return le dossier du jour
     */
    public Path exportDay(LocalDate day) throws Exception {
        Path dayDir = resolveExportRoot().resolve(day.toString());
        Files.createDirectories(dayDir);

        SalesSummary summary = reportService.getSummary(day, day);
        List<OrderHistoryRow> orders = reportService.getOrderHistory(day, day);
        List<OrderLineExportRow> lines = reportService.getOrderLineExports(day, day);
        List<TopItem> topItems = reportService.getTopItems(day, day, 0x7fffffff);
        List<IngredientUsageRow> ingredients = reportService.getTopIngredientsBySales(day, day, 0);
        List<CashMovementRow> cashMovements = reportService.getCashMovements(day, day);

        StringBuilder sb = new StringBuilder();
        appendDocumentStart(sb, day);
        appendSummarySection(sb, summary);
        appendOrdersSection(sb, orders);
        appendOrderLinesSection(sb, lines);
        appendTopProductsSection(sb, topItems);
        appendIngredientsSection(sb, ingredients);
        appendCashMovementsSection(sb, cashMovements);
        sb.append("</body></html>");

        Path report = dayDir.resolve(day + "_rapport_journalier.xls");
        Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);
        LOG.info("Rapport quotidien {} genere: {}", day, report.toAbsolutePath());
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

    // ------------------------------------------------------------------
    // Construction du document
    // ------------------------------------------------------------------

    private void appendDocumentStart(StringBuilder sb, LocalDate day) {
        ReportHtml.documentStart(sb, "Rapport journalier — "
                + ReportHtml.capitalize(DAY_TITLE_FORMAT.format(day)) + " (" + day + ")");
    }

    private void appendSummarySection(StringBuilder sb, SalesSummary s) {
        sb.append("<h2>Resume de la journee</h2><table>")
          .append("<tr><th>Indicateur</th><th class=\"num\">Valeur</th></tr>");
        summaryRow(sb, "Chiffre d'affaires total", money(s.total()), false, false);
        summaryRow(sb, "Nombre de commandes", String.valueOf(s.orderCount()), true, false);
        summaryRow(sb, "Encaissements especes", money(s.cashTotal()), false, false);
        summaryRow(sb, "Encaissements prepayes", money(s.prepaidTotal()), true, false);
        summaryRow(sb, "Cout ingredients", money(s.ingredientCost()), false, false);
        summaryRow(sb, "Marge brute", money(s.grossProfit()), true, false);
        summaryRow(sb, "Marge brute %", ReportHtml.percent(s.grossProfit(), s.total()), false, false);
        summaryRow(sb, "Retraits caisse", money(s.cashWithdrawals()), true, false);
        summaryRow(sb, "REVENU NET", money(s.netRevenue()), false, true);
        sb.append("</table>");
    }

    private void summaryRow(StringBuilder sb, String label, String value, boolean alt, boolean total) {
        sb.append("<tr").append(total ? " class=\"total\"" : alt ? " class=\"alt\"" : "").append(">")
          .append("<td").append(total ? "" : " class=\"kpi\"").append(">").append(escape(label)).append("</td>")
          .append("<td class=\"num\">").append(escape(value)).append("</td></tr>");
    }

    private void appendOrdersSection(StringBuilder sb, List<OrderHistoryRow> rows) {
        sb.append("<h2>Commandes (").append(rows.size()).append(")</h2><table>")
          .append("<tr><th>N°</th><th>Heure</th><th class=\"num\">Articles</th><th class=\"num\">Total</th>")
          .append("<th class=\"num\">Cout ingredients</th><th class=\"num\">Marge</th>")
          .append("<th>Paiement</th><th>Client</th><th>Vendeur</th></tr>");
        double totalSum = 0;
        double marginSum = 0;
        int i = 0;
        for (OrderHistoryRow r : rows) {
            totalSum += r.total();
            marginSum += r.grossProfit();
            sb.append(rowStart(i++))
              .append(td(String.valueOf(r.orderId())))
              .append(td(timeOf(r.createdAt())))
              .append(tdNum(String.valueOf(r.itemCount())))
              .append(tdNum(money(r.total())))
              .append(tdNum(money(r.ingredientCost())))
              .append(tdNum(money(r.grossProfit())))
              .append(td(r.paymentType() == null ? "" : r.paymentType().name()))
              .append(td(r.clientName()))
              .append(td(r.userName()))
              .append("</tr>");
        }
        sb.append("<tr class=\"total\"><td colspan=\"3\">TOTAL</td>")
          .append("<td class=\"num\">").append(money(totalSum)).append("</td>")
          .append("<td></td>")
          .append("<td class=\"num\">").append(money(marginSum)).append("</td>")
          .append("<td colspan=\"3\"></td></tr>")
          .append("</table>");
    }

    private void appendOrderLinesSection(StringBuilder sb, List<OrderLineExportRow> rows) {
        sb.append("<h2>Details des ventes</h2><table>")
          .append("<tr><th>Commande</th><th>Heure</th><th>Produit</th><th class=\"num\">Qte</th>")
          .append("<th class=\"num\">Prix unitaire</th><th class=\"num\">Total ligne</th>")
          .append("<th>Options</th><th>Paiement</th><th>Client</th><th>Vendeur</th></tr>");
        int i = 0;
        for (OrderLineExportRow r : rows) {
            sb.append(rowStart(i++))
              .append(td(String.valueOf(r.orderId())))
              .append(td(timeOf(r.createdAt())))
              .append(td(r.productName()))
              .append(tdNum(String.valueOf(r.quantity())))
              .append(tdNum(money(r.unitPrice())))
              .append(tdNum(money(r.lineTotal())))
              .append(td(r.tags()))
              .append(td(r.paymentType()))
              .append(td(r.clientName()))
              .append(td(r.userName()))
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendTopProductsSection(StringBuilder sb, List<TopItem> rows) {
        sb.append("<h2>Top produits</h2><table>")
          .append("<tr><th>Produit</th><th class=\"num\">Quantite vendue</th>")
          .append("<th class=\"num\">Chiffre d'affaires</th></tr>");
        int i = 0;
        for (TopItem r : rows) {
            sb.append(rowStart(i++))
              .append(td(r.name()))
              .append(tdNum(String.valueOf(r.quantity())))
              .append(tdNum(money(r.revenue())))
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendIngredientsSection(StringBuilder sb, List<IngredientUsageRow> rows) {
        sb.append("<h2>Ingredients consommes</h2><table>")
          .append("<tr><th>Ingredient</th><th>Unite</th><th class=\"num\">Quantite</th>")
          .append("<th class=\"num\">Cout total</th></tr>");
        int i = 0;
        for (IngredientUsageRow r : rows) {
            sb.append(rowStart(i++))
              .append(td(r.name()))
              .append(td(r.unit()))
              .append(tdNum(quantity(r.quantity())))
              .append(tdNum(money(r.totalCost())))
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendCashMovementsSection(StringBuilder sb, List<CashMovementRow> rows) {
        sb.append("<h2>Mouvements de caisse</h2><table>")
          .append("<tr><th>Heure</th><th>Type</th><th>Categorie</th><th class=\"num\">Montant</th>")
          .append("<th>Description</th><th>Utilisateur</th></tr>");
        int i = 0;
        for (CashMovementRow r : rows) {
            sb.append(rowStart(i++))
              .append(td(timeOf(r.createdAt())))
              .append(td(r.movementType()))
              .append(td(r.category()))
              .append(tdNum(money(r.amount())))
              .append(td(r.description()))
              .append(td(r.userName()))
              .append("</tr>");
        }
        sb.append("</table>");
    }

    // ------------------------------------------------------------------
    // Helpers (mise en forme partagee avec l'export hebdomadaire)
    // ------------------------------------------------------------------

    private static String rowStart(int index) {
        return ReportHtml.rowStart(index);
    }

    private static String td(String value) {
        return ReportHtml.td(value);
    }

    private static String tdNum(String value) {
        return ReportHtml.tdNum(value);
    }

    private static String timeOf(String createdAt) {
        return ReportHtml.timeOf(createdAt);
    }

    private static String money(double value) {
        return ReportHtml.money(value);
    }

    private static String quantity(double value) {
        return ReportHtml.quantity(value);
    }

    private static String escape(String value) {
        return ReportHtml.escape(value);
    }
}
