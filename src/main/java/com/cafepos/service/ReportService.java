package com.cafepos.service;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.PaymentType;
import com.cafepos.model.ReportRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportService {
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
}
