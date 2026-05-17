package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TagDAO {
    public List<Tag> findByGroupId(int groupId) throws Exception {
        String sql = "SELECT id, group_id, name, price_modifier FROM tags WHERE group_id = ? ORDER BY name";
        List<Tag> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Tag(
                            rs.getInt("id"),
                            rs.getInt("group_id"),
                            rs.getString("name"),
                            rs.getDouble("price_modifier")
                    ));
                }
            }
        }
        return results;
    }

    public int insertTag(int groupId, String name, double priceModifier) throws Exception {
        String sql = "INSERT INTO tags (group_id, name, price_modifier) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setString(2, name);
            ps.setDouble(3, priceModifier);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updateName(int tagId, String name) throws Exception {
        String sql = "UPDATE tags SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    public void updatePrice(int tagId, double priceModifier) throws Exception {
        String sql = "UPDATE tags SET price_modifier = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, priceModifier);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    public void deleteTag(int tagId) throws Exception {
        String sql = "DELETE FROM tags WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tagId);
            ps.executeUpdate();
        }
    }
}
