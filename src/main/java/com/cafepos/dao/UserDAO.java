package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {
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
