package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.TagGroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TagGroupDAO {
    public List<TagGroup> findByProductId(int productId) throws Exception {
        String sql = "SELECT g.id, g.name, g.multi_select " +
                "FROM tag_groups g " +
                "JOIN product_tag_groups pg ON pg.group_id = g.id " +
                "WHERE pg.product_id = ? ORDER BY g.name";
        List<TagGroup> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new TagGroup(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("multi_select") == 1
                    ));
                }
            }
        }
        return results;
    }

    public List<TagGroup> findAll() throws Exception {
        String sql = "SELECT id, name, multi_select FROM tag_groups ORDER BY name";
        List<TagGroup> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new TagGroup(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("multi_select") == 1
                ));
            }
        }
        return results;
    }

    public int insertGroup(String name, boolean multiSelect) throws Exception {
        String sql = "INSERT INTO tag_groups (name, multi_select) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, multiSelect ? 1 : 0);
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
