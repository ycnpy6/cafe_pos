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

    public void updateName(int groupId, String name) throws Exception {
        String sql = "UPDATE tag_groups SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, groupId);
            ps.executeUpdate();
        }
    }

    public void updateMultiSelect(int groupId, boolean multiSelect) throws Exception {
        String sql = "UPDATE tag_groups SET multi_select = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, multiSelect ? 1 : 0);
            ps.setInt(2, groupId);
            ps.executeUpdate();
        }
    }

    public void deleteGroup(int groupId) throws Exception {
        String deleteLinks = "DELETE FROM product_tag_groups WHERE group_id = ?";
        String deleteTags = "DELETE FROM tags WHERE group_id = ?";
        String deleteGroup = "DELETE FROM tag_groups WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(deleteLinks)) {
                ps.setInt(1, groupId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteTags)) {
                ps.setInt(1, groupId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteGroup)) {
                ps.setInt(1, groupId);
                ps.executeUpdate();
            }
            conn.commit();
        }
    }
}
