package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsDAO {
    public String getValue(String key) throws Exception {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        }
        return null;
    }

    public void setValue(String key, String value) throws Exception {
        String sql = "INSERT INTO app_settings (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
