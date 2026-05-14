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
}
