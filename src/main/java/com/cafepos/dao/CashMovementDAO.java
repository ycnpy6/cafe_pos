package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.CashMovementRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CashMovementDAO {
    public static final String TYPE_INFLOW = "INFLOW";
    public static final String TYPE_OUTFLOW = "OUTFLOW";

    public static final String CATEGORY_INGREDIENT_PURCHASE = "INGREDIENT_PURCHASE";
    public static final String CATEGORY_SHOPPING = "SHOPPING";
    public static final String CATEGORY_WITHDRAWAL = "WITHDRAWAL";
    public static final String CATEGORY_OTHER = "OTHER";

    public void insertMovement(Connection conn,
                               String movementType,
                               String category,
                               double amount,
                               String description,
                               Integer workPeriodId,
                               Integer ingredientId,
                               Integer userId) throws Exception {
        String sql = "INSERT INTO cash_movements "
                + "(movement_type, category, amount, description, work_period_id, ingredient_id, user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movementType);
            ps.setString(2, category);
            ps.setDouble(3, amount);
            ps.setString(4, description);

            if (workPeriodId == null) {
                ps.setObject(5, null);
            } else {
                ps.setInt(5, workPeriodId);
            }

            if (ingredientId == null) {
                ps.setObject(6, null);
            } else {
                ps.setInt(6, ingredientId);
            }

            if (userId == null) {
                ps.setObject(7, null);
            } else {
                ps.setInt(7, userId);
            }
            ps.executeUpdate();
        }
    }

    public List<CashMovementRow> findByDateRange(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT cm.id, cm.created_at, cm.movement_type, cm.category, cm.amount, "
                + "COALESCE(cm.description, '') AS description, "
                + "COALESCE(u.name, '') AS user_name "
                + "FROM cash_movements cm "
                + "LEFT JOIN users u ON u.id = cm.user_id "
                + "WHERE date(cm.created_at) BETWEEN ? AND ? "
                + "ORDER BY cm.created_at DESC, cm.id DESC";

        List<CashMovementRow> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, start.toString());
            ps.setString(2, end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new CashMovementRow(
                            rs.getInt("id"),
                            rs.getString("created_at"),
                            rs.getString("movement_type"),
                            rs.getString("category"),
                            rs.getDouble("amount"),
                            rs.getString("description"),
                            rs.getString("user_name")
                    ));
                }
            }
        }
        return results;
    }

    public double computeExpectedCash(Integer workPeriodId) throws Exception {
        String salesSql = "SELECT COALESCE(SUM(cash_amount), 0) AS total_cash FROM orders WHERE (? IS NULL OR work_period_id = ?)";
        String outflowSql = "SELECT COALESCE(SUM(amount), 0) AS total_outflow FROM cash_movements "
                + "WHERE movement_type = 'OUTFLOW' AND (? IS NULL OR work_period_id = ?)";

        try (Connection conn = DatabaseManager.openConnection()) {
            double cashIn = 0;
            double cashOut = 0;

            try (PreparedStatement ps = conn.prepareStatement(salesSql)) {
                if (workPeriodId == null) {
                    ps.setObject(1, null);
                    ps.setObject(2, null);
                } else {
                    ps.setInt(1, workPeriodId);
                    ps.setInt(2, workPeriodId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cashIn = rs.getDouble("total_cash");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(outflowSql)) {
                if (workPeriodId == null) {
                    ps.setObject(1, null);
                    ps.setObject(2, null);
                } else {
                    ps.setInt(1, workPeriodId);
                    ps.setInt(2, workPeriodId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cashOut = rs.getDouble("total_outflow");
                    }
                }
            }
            return cashIn - cashOut;
        }
    }
}
