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

    public void updatePin(int userId, String pinHash) throws Exception {
        String sql = "UPDATE users SET pin = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pinHash);
            ps.setInt(2, userId);
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

    public User findFirstByRole(UserRole role) throws Exception {
        String sql = "SELECT id, name, pin, role FROM users WHERE role = ? ORDER BY id LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
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

    public List<User> findByRole(UserRole role) throws Exception {
        String sql = "SELECT id, name, pin, role FROM users WHERE role = ? ORDER BY name";
        List<User> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("pin"),
                            UserRole.valueOf(rs.getString("role"))
                    ));
                }
            }
        }
        return results;
    }

    public User findByIdAndPin(int userId, String pinHash) throws Exception {
        String sql = "SELECT id, name, pin, role FROM users WHERE id = ? AND pin = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, pinHash);
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

    public User findByPinAndMinRole(String pinHash, UserRole minRole) throws Exception {
        if (minRole == UserRole.MANAGER) {
            return findByPinAndRole(pinHash, UserRole.MANAGER);
        }

        String sql = "SELECT id, name, pin, role FROM users WHERE pin = ? AND role IN (?, ?) LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pinHash);
            ps.setString(2, UserRole.BARISTA.name());
            ps.setString(3, UserRole.MANAGER.name());
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
