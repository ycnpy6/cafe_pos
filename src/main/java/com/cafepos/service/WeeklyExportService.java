package com.cafepos.service;

import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.SalesSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Export hebdomadaire : un classeur stylise par semaine (lundi-dimanche) dans
 * "<racine>/semaines/", regenere a chaque cloture de journee (idempotent, le
 * fichier de la semaine en cours est toujours a jour). Contenu : resume de la
 * semaine, ventilation par jour, performance par produit et fiches recettes
 * detaillees (cout par portion, ingredient par ingredient) — tous avec le
 * pourcentage de marge.
 */
public class WeeklyExportService {
    private static final Logger LOG = LoggerFactory.getLogger(WeeklyExportService.class);
    private static final String EXPORT_DIR_KEY = "export.default.dir";
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final ReportService reportService = new ReportService();
    private final ProductIngredientDAO productIngredientDAO = new ProductIngredientDAO();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    /** Performance de vente d'un produit sur la semaine. */
    private record ProductWeekRow(int productId, String name, double price, double qty, double revenue) {
    }

    /** Produit actif possedant une recette. */
    private record RecipeProduct(int productId, String name, double price) {
    }

    /**
     * Genere le rapport de la semaine contenant le jour donne dans
     * "<racine>/semaines/semaine_<lundi>_au_<dimanche>.xls".
     *
     * @return le fichier genere
     */
    public Path exportWeekContaining(LocalDate day) throws Exception {
        LocalDate monday = day.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        Path weekDir = resolveExportRoot().resolve("semaines");
        Files.createDirectories(weekDir);

        SalesSummary weekSummary = reportService.getSummary(monday, sunday);
        List<ProductWeekRow> productRows = loadProductPerformance(monday, sunday);
        List<RecipeProduct> recipeProducts = loadRecipeProducts();

        StringBuilder sb = new StringBuilder();
        ReportHtml.documentStart(sb, "Rapport hebdomadaire — du " + monday + " au " + sunday);
        appendWeekSummary(sb, weekSummary);
        appendDayBreakdown(sb, monday);
        appendProductPerformance(sb, productRows);
        appendRecipeSheets(sb, recipeProducts);
        ReportHtml.documentEnd(sb);

        Path report = weekDir.resolve("semaine_" + monday + "_au_" + sunday + ".xls");
        Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);
        LOG.info("Rapport hebdomadaire {} -> {} genere: {}", monday, sunday, report.toAbsolutePath());
        return report;
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void appendWeekSummary(StringBuilder sb, SalesSummary s) {
        sb.append("<h2>Resume de la semaine</h2><table>")
          .append("<tr><th>Indicateur</th><th class=\"num\">Valeur</th></tr>");
        kpiRow(sb, "Chiffre d'affaires total", ReportHtml.money(s.total()), false, false);
        kpiRow(sb, "Nombre de commandes", String.valueOf(s.orderCount()), true, false);
        kpiRow(sb, "Encaissements especes", ReportHtml.money(s.cashTotal()), false, false);
        kpiRow(sb, "Encaissements prepayes", ReportHtml.money(s.prepaidTotal()), true, false);
        kpiRow(sb, "Cout ingredients", ReportHtml.money(s.ingredientCost()), false, false);
        kpiRow(sb, "Marge brute", ReportHtml.money(s.grossProfit()), true, false);
        kpiRow(sb, "Marge brute %", ReportHtml.percent(s.grossProfit(), s.total()), false, false);
        kpiRow(sb, "Retraits caisse", ReportHtml.money(s.cashWithdrawals()), true, false);
        kpiRow(sb, "REVENU NET", ReportHtml.money(s.netRevenue()), false, true);
        sb.append("</table>");
    }

    private void kpiRow(StringBuilder sb, String label, String value, boolean alt, boolean total) {
        sb.append("<tr").append(total ? " class=\"total\"" : alt ? " class=\"alt\"" : "").append(">")
          .append("<td").append(total ? "" : " class=\"kpi\"").append(">").append(ReportHtml.escape(label)).append("</td>")
          .append("<td class=\"num\">").append(ReportHtml.escape(value)).append("</td></tr>");
    }

    private void appendDayBreakdown(StringBuilder sb, LocalDate monday) throws Exception {
        sb.append("<h2>Ventilation par jour</h2><table>")
          .append("<tr><th>Jour</th><th class=\"num\">Commandes</th><th class=\"num\">Chiffre d'affaires</th>")
          .append("<th class=\"num\">Cout ingredients</th><th class=\"num\">Marge</th>")
          .append("<th class=\"num\">Marge %</th></tr>");
        double totalSales = 0;
        double totalCost = 0;
        double totalMargin = 0;
        int totalOrders = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            SalesSummary s = reportService.getSummary(d, d);
            totalSales += s.total();
            totalCost += s.ingredientCost();
            totalMargin += s.grossProfit();
            totalOrders += s.orderCount();
            sb.append(ReportHtml.rowStart(i))
              .append(ReportHtml.td(ReportHtml.capitalize(DAY_FORMAT.format(d))))
              .append(ReportHtml.tdNum(String.valueOf(s.orderCount())))
              .append(ReportHtml.tdNum(ReportHtml.money(s.total())))
              .append(ReportHtml.tdNum(ReportHtml.money(s.ingredientCost())))
              .append(ReportHtml.tdNum(ReportHtml.money(s.grossProfit())))
              .append(ReportHtml.tdNum(ReportHtml.percent(s.grossProfit(), s.total())))
              .append("</tr>");
        }
        sb.append("<tr class=\"total\"><td>TOTAL</td>")
          .append("<td class=\"num\">").append(totalOrders).append("</td>")
          .append("<td class=\"num\">").append(ReportHtml.money(totalSales)).append("</td>")
          .append("<td class=\"num\">").append(ReportHtml.money(totalCost)).append("</td>")
          .append("<td class=\"num\">").append(ReportHtml.money(totalMargin)).append("</td>")
          .append("<td class=\"num\">").append(ReportHtml.percent(totalMargin, totalSales)).append("</td></tr>")
          .append("</table>");
    }

    private void appendProductPerformance(StringBuilder sb, List<ProductWeekRow> rows) throws Exception {
        sb.append("<h2>Performance par produit</h2><table>")
          .append("<tr><th>Produit</th><th class=\"num\">Qte vendue</th><th class=\"num\">Chiffre d'affaires</th>")
          .append("<th class=\"num\">Cout recette / portion</th><th class=\"num\">Cout total</th>")
          .append("<th class=\"num\">Marge</th><th class=\"num\">Marge %</th></tr>");
        int i = 0;
        for (ProductWeekRow r : rows) {
            double costPerPortion = recipeCostPerPortion(r.productId());
            double totalCost = costPerPortion * r.qty();
            double margin = r.revenue() - totalCost;
            sb.append(ReportHtml.rowStart(i++))
              .append(ReportHtml.td(r.name()))
              .append(ReportHtml.tdNum(ReportHtml.quantity(r.qty())))
              .append(ReportHtml.tdNum(ReportHtml.money(r.revenue())))
              .append(ReportHtml.tdNum(ReportHtml.money(costPerPortion)))
              .append(ReportHtml.tdNum(ReportHtml.money(totalCost)))
              .append(ReportHtml.tdNum(ReportHtml.money(margin)))
              .append(ReportHtml.tdNum(ReportHtml.percent(margin, r.revenue())))
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendRecipeSheets(StringBuilder sb, List<RecipeProduct> products) throws Exception {
        sb.append("<h2>Recettes detaillees (cout par portion)</h2>");
        for (RecipeProduct product : products) {
            List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(product.productId());
            if (recipe == null || recipe.isEmpty()) {
                continue;
            }
            sb.append("<h3>").append(ReportHtml.escape(product.name())).append("</h3><table>")
              .append("<tr><th>Ingredient</th><th>Unite</th><th class=\"num\">Quantite / portion</th>")
              .append("<th class=\"num\">Cout unitaire</th><th class=\"num\">Cout / portion</th></tr>");
            double costPerPortion = 0;
            int i = 0;
            for (ProductIngredientUsage usage : recipe) {
                double lineCost = usage.quantityPerProduct() * usage.unitCost();
                costPerPortion += lineCost;
                sb.append(ReportHtml.rowStart(i++))
                  .append(ReportHtml.td(usage.ingredientName()))
                  .append(ReportHtml.td(usage.unit()))
                  .append(ReportHtml.tdNum(ReportHtml.quantity(usage.quantityPerProduct())))
                  .append(ReportHtml.tdNum(ReportHtml.money(usage.unitCost())))
                  .append(ReportHtml.tdNum(ReportHtml.money(lineCost)))
                  .append("</tr>");
            }
            double margin = product.price() - costPerPortion;
            sb.append("<tr class=\"total\"><td colspan=\"4\">Cout total / portion</td>")
              .append("<td class=\"num\">").append(ReportHtml.money(costPerPortion)).append("</td></tr>")
              .append("<tr class=\"total\"><td colspan=\"4\">Prix de vente</td>")
              .append("<td class=\"num\">").append(ReportHtml.money(product.price())).append("</td></tr>")
              .append("<tr class=\"total\"><td colspan=\"4\">Marge / portion (")
              .append(ReportHtml.percent(margin, product.price())).append(")</td>")
              .append("<td class=\"num\">").append(ReportHtml.money(margin)).append("</td></tr>")
              .append("</table>");
        }
    }

    // ------------------------------------------------------------------
    // Donnees
    // ------------------------------------------------------------------

    private List<ProductWeekRow> loadProductPerformance(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT p.id, p.name, p.price, "
                + "SUM(ol.quantity) AS qty, SUM(ol.line_total) AS revenue "
                + "FROM order_lines ol "
                + "JOIN orders o ON o.id = ol.order_id "
                + "JOIN products p ON p.id = ol.product_id "
                + "WHERE date(o.created_at) BETWEEN ? AND ? "
                + "GROUP BY p.id, p.name, p.price "
                + "ORDER BY revenue DESC";
        List<ProductWeekRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ProductWeekRow(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getDouble("qty"),
                            rs.getDouble("revenue")));
                }
            }
        }
        return rows;
    }

    private List<RecipeProduct> loadRecipeProducts() throws Exception {
        String sql = "SELECT DISTINCT p.id, p.name, p.price "
                + "FROM products p "
                + "JOIN product_ingredients pi ON pi.product_id = p.id "
                + "WHERE p.active = 1 "
                + "ORDER BY p.name";
        List<RecipeProduct> rows = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new RecipeProduct(rs.getInt("id"), rs.getString("name"), rs.getDouble("price")));
            }
        }
        return rows;
    }

    private double recipeCostPerPortion(int productId) throws Exception {
        List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(productId);
        double cost = 0;
        for (ProductIngredientUsage usage : recipe) {
            cost += usage.quantityPerProduct() * usage.unitCost();
        }
        return cost;
    }

    private Path resolveExportRoot() {
        Path fallback = DailyExportService.defaultExportDir();
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
}
