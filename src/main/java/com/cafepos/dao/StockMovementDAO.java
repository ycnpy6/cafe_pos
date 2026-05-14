package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class StockMovementDAO {
    public void insertMovement(Connection conn, int productId, int quantity, String reason) throws Exception {
        String sql = "INSERT INTO stock_movements (product_id, quantity, reason) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, quantity);
            ps.setString(3, reason);
            ps.executeUpdate();
        }
    }
}
