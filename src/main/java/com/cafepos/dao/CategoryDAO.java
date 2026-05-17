package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {
    public List<Category> findAll() throws Exception {
        String sql = "SELECT id, name, sort_order FROM categories ORDER BY sort_order, name";
        List<Category> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Category(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("sort_order")
                ));
            }
        }
        return results;
    }

    public int insertCategory(String name, int sortOrder) throws Exception {
        String sql = "INSERT INTO categories (name, sort_order) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, sortOrder);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public int getMaxSortOrder() throws Exception {
        String sql = "SELECT COALESCE(MAX(sort_order), 0) AS max_order FROM categories";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("max_order");
            }
        }
        return 0;
    }

    public void updateName(int categoryId, String name) throws Exception {
        String sql = "UPDATE categories SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            ps.executeUpdate();
        }
    }

    public void updateSortOrder(int categoryId, int sortOrder) throws Exception {
        String sql = "UPDATE categories SET sort_order = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sortOrder);
            ps.setInt(2, categoryId);
            ps.executeUpdate();
        }
    }

    public void deleteCategory(int categoryId) throws Exception {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.executeUpdate();
        }
    }
}
