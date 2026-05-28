package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.ProductIngredientUsage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ProductIngredientDAO {
    public List<ProductIngredientUsage> findRecipeByProduct(int productId) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findRecipeByProduct(conn, productId);
        }
    }

    public List<ProductIngredientUsage> findRecipeByProduct(Connection conn, int productId) throws Exception {
        String sql = "SELECT i.id AS ingredient_id, i.name AS ingredient_name, i.unit, "
                + "pi.quantity, "
                + "CASE WHEN i.package_size <= 0 THEN 0 ELSE i.package_price / i.package_size END AS unit_cost, "
                + "i.stock_quantity "
                + "FROM product_ingredients pi "
                + "JOIN ingredients i ON i.id = pi.ingredient_id "
                + "WHERE pi.product_id = ? "
                + "ORDER BY i.name";
        List<ProductIngredientUsage> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ProductIngredientUsage(
                            rs.getInt("ingredient_id"),
                            rs.getString("ingredient_name"),
                            rs.getString("unit"),
                            rs.getDouble("quantity"),
                            rs.getDouble("unit_cost"),
                            rs.getDouble("stock_quantity")
                    ));
                }
            }
        }
        return results;
    }

    public void upsertRecipeLine(int productId, int ingredientId, double quantity) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            upsertRecipeLine(conn, productId, ingredientId, quantity);
        }
    }

    public void upsertRecipeLine(Connection conn, int productId, int ingredientId, double quantity) throws Exception {
        String sql = "INSERT INTO product_ingredients (product_id, ingredient_id, quantity) VALUES (?, ?, ?) "
                + "ON CONFLICT(product_id, ingredient_id) DO UPDATE SET quantity = excluded.quantity";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, ingredientId);
            ps.setDouble(3, quantity);
            ps.executeUpdate();
        }
    }

    public void deleteRecipeLine(int productId, int ingredientId) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            deleteRecipeLine(conn, productId, ingredientId);
        }
    }

    public void deleteRecipeLine(Connection conn, int productId, int ingredientId) throws Exception {
        String sql = "DELETE FROM product_ingredients WHERE product_id = ? AND ingredient_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, ingredientId);
            ps.executeUpdate();
        }
    }
}
