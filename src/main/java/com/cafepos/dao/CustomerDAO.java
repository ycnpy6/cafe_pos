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
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            rs.getInt("active") == 1
                    );
                }
            }
        }
        return null;
    }

    public Customer findByCardUid(String cardUid) throws Exception {
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE card_uid = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            rs.getInt("active") == 1
                    );
                }
            }
        }
        return null;
    }

    public Customer findActiveByCardUid(String cardUid) throws Exception {
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE card_uid = ? AND active = 1 LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            true
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
        String sql = "SELECT id, name, card_uid, balance, active FROM customers ORDER BY name";
        List<Customer> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Customer(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("card_uid"),
                        rs.getDouble("balance"),
                        rs.getInt("active") == 1
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

    public int insertCustomer(String name, String cardUid, double balance, boolean active) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return insertCustomer(conn, name, cardUid, balance, active);
        }
    }

    public int insertCustomer(Connection conn, String name, String cardUid, double balance) throws Exception {
        return insertCustomer(conn, name, cardUid, balance, true);
    }

    public int insertCustomer(Connection conn, String name, String cardUid, double balance, boolean active) throws Exception {
        String sql = "INSERT INTO customers (name, card_uid, balance, active) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, cardUid);
            ps.setDouble(3, balance);
            ps.setInt(4, active ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updateName(Connection conn, int customerId, String name) throws Exception {
        String sql = "UPDATE customers SET name = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateCardUid(Connection conn, int customerId, String cardUid) throws Exception {
        String sql = "UPDATE customers SET card_uid = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardUid);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateActive(Connection conn, int customerId, boolean active) throws Exception {
        String sql = "UPDATE customers SET active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void deleteCustomer(Connection conn, int customerId) throws Exception {
        String sql = "DELETE FROM customers WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        }
    }
}
