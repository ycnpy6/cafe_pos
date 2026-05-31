package com.cafepos.service;

import com.cafepos.dao.CashMovementDAO;
import com.cafepos.model.CashMovementRow;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.OrderHistoryRow;
import com.cafepos.model.OrderLineDetail;
import com.cafepos.model.PaymentType;
import com.cafepos.model.ReportRow;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.SessionRow;
import com.cafepos.model.TopItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportService {
    private final CashMovementDAO cashMovementDAO = new CashMovementDAO();

    public List<ReportRow> getOrders(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT id, total, payment_type, created_at FROM orders " +
                "WHERE date(created_at) BETWEEN ? AND ? ORDER BY created_at DESC";
        List<ReportRow> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ReportRow(
                            rs.getInt("id"),
                            rs.getString("created_at"),
                            PaymentType.valueOf(rs.getString("payment_type")),
                            rs.getDouble("total")
                    ));
                }
            }
        }
        return results;
    }

    public double getTotalSales(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT COALESCE(SUM(total), 0) AS total_sales FROM orders " +
                "WHERE date(created_at) BETWEEN ? AND ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total_sales");
                }
            }
        }
        return 0;
    }

    public int getOrderCount(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT COUNT(*) AS order_count FROM orders WHERE date(created_at) BETWEEN ? AND ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("order_count");
                }
            }
        }
        return 0;
    }

    public SalesSummary getSummary(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT COALESCE(SUM(total), 0) AS total_sales, "
                + "COUNT(*) AS order_count, "
                + "COALESCE(SUM(cash_amount), 0) AS cash_total, "
                + "COALESCE(SUM(prepaid_amount), 0) AS prepaid_total, "
                + "COALESCE((SELECT SUM(im.total_cost) FROM ingredient_movements im "
                + "WHERE date(im.created_at) BETWEEN ? AND ? "
                + "AND im.reason IN ('SALE', 'REFUND')), 0) AS ingredient_cost, "
                + "COALESCE((SELECT SUM(cm.amount) FROM cash_movements cm "
                + "WHERE date(cm.created_at) BETWEEN ? AND ? "
                + "AND cm.movement_type = 'OUTFLOW'), 0) AS cash_withdrawals "
                + "FROM orders WHERE date(created_at) BETWEEN ? AND ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            ps.setString(3, start.toString());
            ps.setString(4, end.toString());
            ps.setString(5, start.toString());
            ps.setString(6, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double totalSales = rs.getDouble("total_sales");
                    double ingredientCost = rs.getDouble("ingredient_cost");
                    double cashWithdrawals = rs.getDouble("cash_withdrawals");
                    double grossProfit = totalSales - ingredientCost;
                    return new SalesSummary(
                            totalSales,
                            rs.getInt("order_count"),
                            rs.getDouble("cash_total"),
                            rs.getDouble("prepaid_total"),
                            ingredientCost,
                            grossProfit,
                            cashWithdrawals,
                            grossProfit - cashWithdrawals
                    );
                }
            }
        }
        return new SalesSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public List<CashMovementRow> getCashMovements(LocalDate start, LocalDate end) throws Exception {
        return cashMovementDAO.findByDateRange(start, end);
    }

    public List<TopItem> getTopItems(LocalDate start, LocalDate end, int limit) throws Exception {
        String sql = "SELECT p.name AS name, SUM(ol.quantity) AS qty, SUM(ol.line_total) AS revenue "
                + "FROM order_lines ol "
                + "JOIN orders o ON o.id = ol.order_id "
                + "JOIN products p ON p.id = ol.product_id "
                + "WHERE date(o.created_at) BETWEEN ? AND ? "
                + "GROUP BY p.id, p.name "
                + "ORDER BY qty DESC LIMIT ?";
        List<TopItem> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new TopItem(
                            rs.getString("name"),
                            rs.getInt("qty"),
                            rs.getDouble("revenue")
                    ));
                }
            }
        }
        return results;
    }

    public List<OrderHistoryRow> getOrderHistory(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT o.id, o.created_at, o.total, o.payment_type, "
                + "COALESCE((SELECT SUM(im.total_cost) FROM ingredient_movements im "
                + "WHERE im.order_id = o.id AND im.reason IN ('SALE', 'REFUND')), 0) AS ingredient_cost, "
            + "c.id AS client_id, COALESCE(c.name, '') AS client_name, "
                + "COALESCE(u.name, '') AS user_name, SUM(ol.quantity) AS items "
                + "FROM orders o "
                + "JOIN order_lines ol ON ol.order_id = o.id "
            + "LEFT JOIN customers c ON c.id = o.customer_id "
                + "LEFT JOIN users u ON u.id = o.user_id "
                + "WHERE date(o.created_at) BETWEEN ? AND ? "
            + "GROUP BY o.id, o.created_at, o.total, o.payment_type, c.id, c.name, u.name "
                + "ORDER BY o.created_at DESC";
        List<OrderHistoryRow> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double total = rs.getDouble("total");
                    double ingredientCost = rs.getDouble("ingredient_cost");
                    int rawClientId = rs.getInt("client_id");
                    Integer clientId = rs.wasNull() ? null : rawClientId;
                    results.add(new OrderHistoryRow(
                            rs.getInt("id"),
                            rs.getString("created_at"),
                            rs.getInt("items"),
                            total,
                            ingredientCost,
                            total - ingredientCost,
                            PaymentType.valueOf(rs.getString("payment_type")),
                            clientId,
                            rs.getString("client_name"),
                            rs.getString("user_name")
                    ));
                }
            }
        }
        return results;
    }

    public List<OrderLineDetail> getOrderDetails(int orderId) throws Exception {
        String sql = "SELECT p.name AS product_name, ol.quantity, ol.line_total, "
                + "GROUP_CONCAT(t.name, ', ') AS tags "
                + "FROM order_lines ol "
                + "JOIN products p ON p.id = ol.product_id "
                + "LEFT JOIN order_line_tags olt ON olt.line_id = ol.id "
                + "LEFT JOIN tags t ON t.id = olt.tag_id "
                + "WHERE ol.order_id = ? "
                + "GROUP BY ol.id, p.name, ol.quantity, ol.line_total";
        List<OrderLineDetail> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new OrderLineDetail(
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("line_total"),
                            rs.getString("tags")
                    ));
                }
            }
        }
        return results;
    }

    public List<SessionRow> getSessions(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT wp.id, wp.opened_at, wp.closed_at, "
                + "COUNT(o.id) AS orders, "
                + "COALESCE(SUM(o.total), 0) AS total_sales, "
                + "COALESCE(SUM(o.cash_amount), 0) AS cash_total, "
                + "COALESCE(SUM(o.prepaid_amount), 0) AS prepaid_total "
                + "FROM work_periods wp "
                + "LEFT JOIN orders o ON o.work_period_id = wp.id "
                + "WHERE date(wp.opened_at) BETWEEN ? AND ? "
                + "GROUP BY wp.id, wp.opened_at, wp.closed_at "
                + "ORDER BY wp.opened_at DESC";
        List<SessionRow> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SessionRow(
                            rs.getInt("id"),
                            rs.getString("opened_at"),
                            rs.getString("closed_at"),
                            rs.getInt("orders"),
                            rs.getDouble("total_sales"),
                            rs.getDouble("cash_total"),
                            rs.getDouble("prepaid_total"),
                            rs.getString("closed_at") == null ? "Ouverte" : "Auto"
                    ));
                }
            }
        }
        return results;
    }
}
