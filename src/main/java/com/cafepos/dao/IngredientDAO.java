package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Ingredient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class IngredientDAO {
    public List<Ingredient> findAll() throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findAll(conn);
        }
    }

    public List<Ingredient> findAll(Connection conn) throws Exception {
        String sql = "SELECT id, name, unit, package_size, package_price, stock_quantity, min_quantity, active "
                + "FROM ingredients ORDER BY name";
        List<Ingredient> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public List<Ingredient> findActive() throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findActive(conn);
        }
    }

    public List<Ingredient> findActive(Connection conn) throws Exception {
        String sql = "SELECT id, name, unit, package_size, package_price, stock_quantity, min_quantity, active "
                + "FROM ingredients WHERE active = 1 ORDER BY name";
        List<Ingredient> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public Ingredient findById(Connection conn, int ingredientId) throws Exception {
        String sql = "SELECT id, name, unit, package_size, package_price, stock_quantity, min_quantity, active "
                + "FROM ingredients WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int insertIngredient(Ingredient ingredient) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return insertIngredient(conn, ingredient);
        }
    }

    public int insertIngredient(Connection conn, Ingredient ingredient) throws Exception {
        String sql = "INSERT INTO ingredients (name, unit, package_size, package_price, stock_quantity, min_quantity, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ingredient.getName());
            ps.setString(2, ingredient.getUnit());
            ps.setDouble(3, ingredient.getPackageSize());
            ps.setDouble(4, ingredient.getPackagePrice());
            ps.setDouble(5, ingredient.getStockQuantity());
            ps.setDouble(6, ingredient.getMinQuantity());
            ps.setInt(7, ingredient.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updateIngredient(Ingredient ingredient) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            updateIngredient(conn, ingredient);
        }
    }

    public void updateIngredient(Connection conn, Ingredient ingredient) throws Exception {
        String sql = "UPDATE ingredients SET name = ?, unit = ?, package_size = ?, package_price = ?, "
                + "stock_quantity = ?, min_quantity = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ingredient.getName());
            ps.setString(2, ingredient.getUnit());
            ps.setDouble(3, ingredient.getPackageSize());
            ps.setDouble(4, ingredient.getPackagePrice());
            ps.setDouble(5, ingredient.getStockQuantity());
            ps.setDouble(6, ingredient.getMinQuantity());
            ps.setInt(7, ingredient.isActive() ? 1 : 0);
            ps.setInt(8, ingredient.getId());
            ps.executeUpdate();
        }
    }

    public void setStockQuantity(Connection conn, int ingredientId, double stockQuantity) throws Exception {
        String sql = "UPDATE ingredients SET stock_quantity = CASE WHEN ? < 0 THEN 0 ELSE ? END WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, stockQuantity);
            ps.setDouble(2, stockQuantity);
            ps.setInt(3, ingredientId);
            ps.executeUpdate();
        }
    }

    public void adjustStock(Connection conn, int ingredientId, double delta) throws Exception {
        String sql = "UPDATE ingredients SET stock_quantity = CASE WHEN stock_quantity + ? < 0 THEN 0 ELSE stock_quantity + ? END "
                + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setDouble(2, delta);
            ps.setInt(3, ingredientId);
            ps.executeUpdate();
        }
    }

    private Ingredient mapRow(ResultSet rs) throws Exception {
        return new Ingredient(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getDouble("package_size"),
                rs.getDouble("package_price"),
                rs.getDouble("stock_quantity"),
                rs.getDouble("min_quantity"),
                rs.getInt("active") == 1
        );
    }
}
