package com.cafepos.test;

import com.cafepos.service.ReportService;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.TopItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportServiceIntegrationTest {
    private final ReportService reportService = new ReportService();

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
    }

    @Test
    void summaryIncludesSalesAndCostsInRange() throws Exception {
        LocalDate today = LocalDate.now();

        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection()) {
            insertProduct(conn, 1, "Cafe", 1);
            insertOrder(conn, 1001, 100.0, "ESPECES", today.toString());
            insertOrderLine(conn, 2001, 1001, 1, 1, 100.0);
            insertIngredientMovement(conn, 3001, 1, -1.0, "SALE", 30.0, 30.0, today.toString());
            insertCashMovement(conn, 4001, "OUTFLOW", "SHOPPING", 10.0, today.toString());

            insertOrder(conn, 1002, 50.0, "ESPECES", today.minusDays(10).toString());
        }

        SalesSummary summary = reportService.getSummary(today, today);
        assertEquals(100.0, summary.total(), 0.001);
        assertEquals(1, summary.orderCount());
        assertEquals(100.0, summary.cashTotal(), 0.001);
        assertEquals(0.0, summary.prepaidTotal(), 0.001);
        assertEquals(30.0, summary.ingredientCost(), 0.001);
        assertEquals(70.0, summary.grossProfit(), 0.001);
        assertEquals(10.0, summary.cashWithdrawals(), 0.001);
        assertEquals(60.0, summary.netRevenue(), 0.001);
    }

    @Test
    void topItemsOrdersByQuantity() throws Exception {
        LocalDate today = LocalDate.now();

        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection()) {
            insertProduct(conn, 1, "Cafe", 1);
            insertProduct(conn, 2, "The", 1);
            insertOrder(conn, 1001, 100.0, "ESPECES", today.toString());
            insertOrderLine(conn, 2001, 1001, 1, 3, 60.0);
            insertOrderLine(conn, 2002, 1001, 2, 1, 40.0);
        }

        List<TopItem> items = reportService.getTopItems(today, today, 5);
        assertEquals(2, items.size());
        assertEquals("Cafe", items.get(0).name());
        assertEquals(3, items.get(0).quantity());
        assertEquals("The", items.get(1).name());
        assertEquals(1, items.get(1).quantity());
    }

    private void insertProduct(Connection conn, int id, String name, int categoryId) throws Exception {
        String sql = "INSERT INTO products (id, name, price, cost, category_id, stock, active, is_prepared) "
                + "VALUES (?, ?, 0, 0, ?, 0, 1, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setInt(3, categoryId);
            ps.executeUpdate();
        }
    }

    private void insertOrder(Connection conn, int id, double total, String paymentType, String date) throws Exception {
        String sql = "INSERT INTO orders (id, total, payment_type, cash_amount, prepaid_amount, created_at) "
                + "VALUES (?, ?, ?, ?, 0, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setDouble(2, total);
            ps.setString(3, paymentType);
            ps.setDouble(4, total);
            ps.setString(5, date + " 10:00:00");
            ps.executeUpdate();
        }
    }

    private void insertOrderLine(Connection conn, int id, int orderId, int productId, int qty, double lineTotal)
            throws Exception {
        String sql = "INSERT INTO order_lines (id, order_id, product_id, quantity, unit_price, line_total) "
                + "VALUES (?, ?, ?, ?, 0, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, orderId);
            ps.setInt(3, productId);
            ps.setInt(4, qty);
            ps.setDouble(5, lineTotal);
            ps.executeUpdate();
        }
    }

    private void insertIngredientMovement(Connection conn, int id, int ingredientId, double quantity,
                                          String reason, double unitCost, double totalCost, String date)
            throws Exception {
        String sql = "INSERT INTO ingredient_movements "
                + "(id, ingredient_id, quantity, reason, unit_cost, total_cost, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, ingredientId);
            ps.setDouble(3, quantity);
            ps.setString(4, reason);
            ps.setDouble(5, unitCost);
            ps.setDouble(6, totalCost);
            ps.setString(7, date + " 10:00:00");
            ps.executeUpdate();
        }
    }

    private void insertCashMovement(Connection conn, int id, String type, String category, double amount, String date)
            throws Exception {
        String sql = "INSERT INTO cash_movements "
                + "(id, movement_type, category, amount, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, type);
            ps.setString(3, category);
            ps.setDouble(4, amount);
            ps.setString(5, date + " 10:00:00");
            ps.executeUpdate();
        }
    }
}
