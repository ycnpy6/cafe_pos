package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AccountTransactionDAO {
    public void insertTransaction(Connection conn, int customerId, double amount, String description, int userId)
            throws Exception {
        String sql = "INSERT INTO account_transactions (customer_id, amount, description, user_id) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setDouble(2, amount);
            ps.setString(3, description);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
    }
}
