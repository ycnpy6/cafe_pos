package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {
    public List<User> findAll() throws Exception {
        String sql = "SELECT id, name, pin, role FROM users ORDER BY role DESC, name";
        List<User> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new User(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("pin"),
                        UserRole.valueOf(rs.getString("role"))
                ));
            }
        }
        return results;
    }

    public void insertUser(String name, String pinHash, UserRole role) throws Exception {
        String sql = "INSERT INTO users (name, pin, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, pinHash);
            ps.setString(3, role.name());
            ps.executeUpdate();
        }
    }

    public void deleteUser(int id) throws Exception {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public int countByRole(UserRole role) throws Exception {
        String sql = "SELECT COUNT(*) AS total FROM users WHERE role = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }

    public User findByPinAndRole(String pinHash, UserRole role) throws Exception {
        String sql = "SELECT id, name, pin, role FROM users WHERE pin = ? AND role = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pinHash);
            ps.setString(2, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("pin"),
                            UserRole.valueOf(rs.getString("role"))
                    );
                }
            }
        }
        return null;
    }
}
