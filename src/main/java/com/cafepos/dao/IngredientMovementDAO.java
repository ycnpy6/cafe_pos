package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class IngredientMovementDAO {
    public void insertMovement(Connection conn,
                               int ingredientId,
                               double quantity,
                               String reason,
                               double unitCost,
                               double totalCost,
                               Integer workPeriodId,
                               Integer orderId,
                               Integer userId) throws Exception {
        String sql = "INSERT INTO ingredient_movements "
                + "(ingredient_id, quantity, reason, unit_cost, total_cost, work_period_id, order_id, user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            ps.setDouble(2, quantity);
            ps.setString(3, reason);
            ps.setDouble(4, unitCost);
            ps.setDouble(5, totalCost);
            if (workPeriodId == null) {
                ps.setObject(6, null);
            } else {
                ps.setInt(6, workPeriodId);
            }
            if (orderId == null) {
                ps.setObject(7, null);
            } else {
                ps.setInt(7, orderId);
            }
            if (userId == null) {
                ps.setObject(8, null);
            } else {
                ps.setInt(8, userId);
            }
            ps.executeUpdate();
        }
    }
}
