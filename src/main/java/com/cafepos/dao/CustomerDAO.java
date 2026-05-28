package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class CustomerDAO {
    public Customer findById(int customerId) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findById(conn, customerId);
        }
    }

    public Customer findById(Connection conn, int customerId) throws Exception {
        String sql = "SELECT id, name, card_uid, balance FROM customers WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance")
                    );
                }
            }
        }
        return null;
    }

    public Customer findByCardUid(String cardUid) throws Exception {
        String sql = "SELECT id, name, card_uid, balance FROM customers WHERE card_uid = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance")
                    );
                }
            }
        }
        return null;
    }

    public void updateBalance(Connection conn, int customerId, double newBalance) throws Exception {
        String sql = "UPDATE customers SET balance = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public List<Customer> findAll() throws Exception {
        String sql = "SELECT id, name, card_uid, balance FROM customers ORDER BY name";
        List<Customer> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Customer(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("card_uid"),
                        rs.getDouble("balance")
                ));
            }
        }
        return results;
    }

    public int insertCustomer(String name, String cardUid, double balance) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return insertCustomer(conn, name, cardUid, balance);
        }
    }

    public int insertCustomer(Connection conn, String name, String cardUid, double balance) throws Exception {
        String sql = "INSERT INTO customers (name, card_uid, balance) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, cardUid);
            ps.setDouble(3, balance);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }
}
