package com.cafepos.test;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.service.WeeklyExportService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyExportIntegrationTest {

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
    }

    @Test
    void exportWeekWritesRecipeDetailsWithMarginPercent() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        Path exportRoot = Files.createTempDirectory("cafepos-week-export-test");
        new SettingsDAO().setValue("export.default.dir", exportRoot.toString());

        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection()) {
            // Produit 300 DA avec une recette a 30 DA (15 G de cafe a 2 DA/G)
            // -> marge 270 DA soit 90,0 % du prix de vente.
            insertProduct(conn, 1, "Espresso Test", 300.0);
            insertIngredient(conn, 1, "Cafe Moulu Test", "G", 1000.0, 2000.0);
            insertRecipeLine(conn, 1, 1, 15.0, "G");
            insertOrder(conn, 8001, 300.0, today.toString());
            insertOrderLine(conn, 8101, 8001, 1, 1, 300.0);
        }

        Path report = new WeeklyExportService().exportWeekContaining(today);

        assertTrue(Files.exists(report), "rapport hebdomadaire manquant: " + report);
        assertTrue(report.getFileName().toString().startsWith("semaine_" + monday),
                "nom de fichier attendu sur le lundi: " + report.getFileName());

        String html = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(html.contains("Rapport hebdomadaire"), "titre attendu");
        assertTrue(html.contains("Ventilation par jour"), "ventilation par jour attendue");
        assertTrue(html.contains("Performance par produit"), "performance produit attendue");
        assertTrue(html.contains("Recettes detaillees"), "fiches recettes attendues");
        assertTrue(html.contains("Espresso Test"), "produit attendu");
        assertTrue(html.contains("Cafe Moulu Test"), "ingredient de la recette attendu");
        assertTrue(html.contains("Marge %") || html.contains("Marge brute %"), "colonne marge % attendue");
        assertTrue(html.contains("90,0 %"), "pourcentage de marge attendu (90,0 %): " + extractAround(html, "%"));
    }

    private static String extractAround(String html, String needle) {
        int idx = html.indexOf(needle);
        if (idx < 0) {
            return "(absent)";
        }
        return html.substring(Math.max(0, idx - 120), Math.min(html.length(), idx + 40));
    }

    private void insertProduct(Connection conn, int id, String name, double price) throws Exception {
        String sql = "INSERT INTO products (id, name, price, cost, category_id, stock, active, is_prepared) "
                + "VALUES (?, ?, ?, 0, 1, 0, 1, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setDouble(3, price);
            ps.executeUpdate();
        }
    }

    private void insertIngredient(Connection conn, int id, String name, String unit,
                                  double packageSize, double packagePrice) throws Exception {
        String sql = "INSERT INTO ingredients (id, name, unit, unit_base, unit_factor, package_size, package_price) "
                + "VALUES (?, ?, ?, ?, 1, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, unit);
            ps.setString(4, unit);
            ps.setDouble(5, packageSize);
            ps.setDouble(6, packagePrice);
            ps.executeUpdate();
        }
    }

    private void insertRecipeLine(Connection conn, int productId, int ingredientId,
                                  double quantity, String unit) throws Exception {
        String sql = "INSERT INTO product_ingredients (product_id, ingredient_id, quantity, unit, quantity_base) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, ingredientId);
            ps.setDouble(3, quantity);
            ps.setString(4, unit);
            ps.setDouble(5, quantity);
            ps.executeUpdate();
        }
    }

    private void insertOrder(Connection conn, int id, double total, String date) throws Exception {
        String sql = "INSERT INTO orders (id, total, payment_type, cash_amount, prepaid_amount, created_at) "
                + "VALUES (?, ?, 'ESPECES', ?, 0, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setDouble(2, total);
            ps.setDouble(3, total);
            ps.setString(4, date + " 11:00:00");
            ps.executeUpdate();
        }
    }

    private void insertOrderLine(Connection conn, int id, int orderId, int productId, int qty, double lineTotal)
            throws Exception {
        String sql = "INSERT INTO order_lines (id, order_id, product_id, quantity, unit_price, line_total) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, orderId);
            ps.setInt(3, productId);
            ps.setInt(4, qty);
            ps.setDouble(5, lineTotal);
            ps.setDouble(6, lineTotal);
            ps.executeUpdate();
        }
    }
}
