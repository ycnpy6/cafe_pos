package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class WorkPeriodDAO {
    public Integer findOpenWorkPeriodId(Connection conn) throws Exception {
        String sql = "SELECT id FROM work_periods WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return null;
    }

    public int openWorkPeriod(Connection conn, int userId) throws Exception {
        String sql = "INSERT INTO work_periods (opened_at, opened_by) VALUES (datetime('now'), ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void closeWorkPeriod(Connection conn, int workPeriodId) throws Exception {
        String sql = "UPDATE work_periods SET closed_at = datetime('now'), closed_by = NULL WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workPeriodId);
            ps.executeUpdate();
        }
    }

    public void insertEodReport(Connection conn, int workPeriodId, double totalSales, int orderCount) throws Exception {
        String sql = "INSERT INTO eod_reports (work_period_id, total_sales, order_count) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workPeriodId);
            ps.setDouble(2, totalSales);
            ps.setInt(3, orderCount);
            ps.executeUpdate();
        }
    }

    public double getTotalSalesByWorkPeriod(Connection conn, int workPeriodId) throws Exception {
        String sql = "SELECT COALESCE(SUM(total), 0) AS total_sales FROM orders WHERE work_period_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workPeriodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total_sales");
                }
            }
        }
        return 0;
    }

    public int getOrderCountByWorkPeriod(Connection conn, int workPeriodId) throws Exception {
        String sql = "SELECT COUNT(*) AS order_count FROM orders WHERE work_period_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workPeriodId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("order_count");
                }
            }
        }
        return 0;
    }

    public Integer getOpenWorkPeriodId() throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findOpenWorkPeriodId(conn);
        }
    }
}
