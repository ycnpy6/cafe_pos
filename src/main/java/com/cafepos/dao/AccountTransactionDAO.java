package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.AccountTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountTransactionDAO {
    public void insertTransaction(Connection conn, int customerId, double amount, String description, int userId)
            throws Exception {
        insertTransaction(conn, customerId, amount, description, userId, null, null);
    }

    public void insertTransaction(Connection conn, int customerId, double amount, String description, int userId,
                                  Double balanceAfter, Integer orderId) throws Exception {
        String sql = "INSERT INTO account_transactions (customer_id, amount, description, user_id, balance_after, order_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setDouble(2, amount);
            ps.setString(3, description);
            ps.setInt(4, userId);
            if (balanceAfter == null) {
                ps.setObject(5, null);
            } else {
                ps.setDouble(5, balanceAfter);
            }
            if (orderId == null) {
                ps.setObject(6, null);
            } else {
                ps.setInt(6, orderId);
            }
            ps.executeUpdate();
        }
    }

    public List<AccountTransaction> findRecentByCustomer(int customerId, int limit) throws Exception {
        String sql = "SELECT id, amount, description, created_at, balance_after, order_id "
                + "FROM account_transactions WHERE customer_id = ? "
                + "ORDER BY created_at DESC LIMIT ?";
        List<AccountTransaction> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new AccountTransaction(
                            rs.getInt("id"),
                            customerId,
                            rs.getDouble("amount"),
                            rs.getString("description"),
                            rs.getString("created_at"),
                            rs.getDouble("balance_after"),
                            rs.getObject("order_id") == null ? null : rs.getInt("order_id")
                    ));
                }
            }
        }
        return results;
    }

    public Map<Integer, String> findLastTransactionDates() throws Exception {
        String sql = "SELECT customer_id, MAX(created_at) AS last_tx "
                + "FROM account_transactions GROUP BY customer_id";
        Map<Integer, String> results = new HashMap<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.put(rs.getInt("customer_id"), rs.getString("last_tx"));
            }
        }
        return results;
    }

    public Double findBalanceAfterOrder(int orderId) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findBalanceAfterOrder(conn, orderId);
        }
    }

    public Double findBalanceAfterOrder(Connection conn, int orderId) throws Exception {
        String sql = "SELECT balance_after FROM account_transactions "
                + "WHERE order_id = ? AND balance_after IS NOT NULL "
                + "ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance_after");
                }
            }
        }
        return null;
    }
}
