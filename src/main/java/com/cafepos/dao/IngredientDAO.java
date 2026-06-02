package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Ingredient;
import com.cafepos.model.StockUnit;

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
        String sql = "SELECT id, name, unit, "
            + "COALESCE(unit_base, unit) AS unit_base, "
            + "COALESCE(unit_factor, 1) AS unit_factor, "
            + "package_size, package_price, "
            + "stock_quantity, min_quantity, "
            + "COALESCE(stock_base_quantity, stock_quantity * COALESCE(unit_factor, 1)) AS stock_base_quantity, "
            + "COALESCE(min_base_quantity, min_quantity * COALESCE(unit_factor, 1)) AS min_base_quantity, "
            + "active "
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
        String sql = "SELECT id, name, unit, "
            + "COALESCE(unit_base, unit) AS unit_base, "
            + "COALESCE(unit_factor, 1) AS unit_factor, "
            + "package_size, package_price, "
            + "stock_quantity, min_quantity, "
            + "COALESCE(stock_base_quantity, stock_quantity * COALESCE(unit_factor, 1)) AS stock_base_quantity, "
            + "COALESCE(min_base_quantity, min_quantity * COALESCE(unit_factor, 1)) AS min_base_quantity, "
            + "active "
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
        String sql = "SELECT id, name, unit, "
            + "COALESCE(unit_base, unit) AS unit_base, "
            + "COALESCE(unit_factor, 1) AS unit_factor, "
            + "package_size, package_price, "
            + "stock_quantity, min_quantity, "
            + "COALESCE(stock_base_quantity, stock_quantity * COALESCE(unit_factor, 1)) AS stock_base_quantity, "
            + "COALESCE(min_base_quantity, min_quantity * COALESCE(unit_factor, 1)) AS min_base_quantity, "
            + "active "
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
        String sql = "INSERT INTO ingredients "
            + "(name, unit, unit_base, unit_factor, package_size, package_price, "
            + "stock_quantity, min_quantity, stock_base_quantity, min_base_quantity, active) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        StockUnit stockUnit = StockUnit.fromDisplayUnit(ingredient.getUnit());
        double factor = stockUnit.factorToBase();
        double stockBase = ingredient.getStockBaseQuantity() > 0
            ? ingredient.getStockBaseQuantity()
            : ingredient.getStockQuantity() * factor;
        double minBase = ingredient.getMinBaseQuantity() > 0
            ? ingredient.getMinBaseQuantity()
            : ingredient.getMinQuantity() * factor;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ingredient.getName());
            ps.setString(2, ingredient.getUnit());
            ps.setString(3, stockUnit.unitBase());
            ps.setDouble(4, factor);
            ps.setDouble(5, ingredient.getPackageSize());
            ps.setDouble(6, ingredient.getPackagePrice());
            ps.setDouble(7, ingredient.getStockQuantity());
            ps.setDouble(8, ingredient.getMinQuantity());
            ps.setDouble(9, stockBase);
            ps.setDouble(10, minBase);
            ps.setInt(11, ingredient.isActive() ? 1 : 0);
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
            + "unit_base = ?, unit_factor = ?, stock_quantity = ?, min_quantity = ?, "
            + "stock_base_quantity = ?, min_base_quantity = ?, active = ? WHERE id = ?";
        StockUnit stockUnit = StockUnit.fromDisplayUnit(ingredient.getUnit());
        double factor = stockUnit.factorToBase();
        double stockBase = ingredient.getStockBaseQuantity() > 0
            ? ingredient.getStockBaseQuantity()
            : ingredient.getStockQuantity() * factor;
        double minBase = ingredient.getMinBaseQuantity() > 0
            ? ingredient.getMinBaseQuantity()
            : ingredient.getMinQuantity() * factor;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ingredient.getName());
            ps.setString(2, ingredient.getUnit());
            ps.setDouble(3, ingredient.getPackageSize());
            ps.setDouble(4, ingredient.getPackagePrice());
            ps.setString(5, stockUnit.unitBase());
            ps.setDouble(6, factor);
            ps.setDouble(7, ingredient.getStockQuantity());
            ps.setDouble(8, ingredient.getMinQuantity());
            ps.setDouble(9, stockBase);
            ps.setDouble(10, minBase);
            ps.setInt(11, ingredient.isActive() ? 1 : 0);
            ps.setInt(12, ingredient.getId());
            ps.executeUpdate();
        }
    }

    public void updateActive(Connection conn, int ingredientId, boolean active) throws Exception {
        String sql = "UPDATE ingredients SET active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, ingredientId);
            ps.executeUpdate();
        }
    }

    public void deleteIngredient(Connection conn, int ingredientId) throws Exception {
        String sql = "DELETE FROM ingredients WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            ps.executeUpdate();
        }
    }

    public void setStockQuantity(Connection conn, int ingredientId, double stockQuantity) throws Exception {
        String sql = "UPDATE ingredients "
                + "SET stock_quantity = CASE WHEN ? < 0 THEN 0 ELSE ? END, "
                + "stock_base_quantity = CASE "
                + "  WHEN ? < 0 THEN 0 "
                + "  ELSE (CASE WHEN ? <= 0 THEN ? ELSE ? * ? END) END "
                + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, stockQuantity);
            ps.setDouble(2, stockQuantity);
            ps.setDouble(3, stockQuantity);
            ps.setDouble(4, stockQuantity);
            ps.setDouble(5, stockQuantity);
            ps.setDouble(6, stockQuantity);
            ps.setDouble(7, fetchUnitFactor(conn, ingredientId));
            ps.setInt(8, ingredientId);
            ps.executeUpdate();
        }
    }

    public void adjustStock(Connection conn, int ingredientId, double delta) throws Exception {
        double factor = fetchUnitFactor(conn, ingredientId);
        String sql = "UPDATE ingredients "
                + "SET stock_quantity = CASE WHEN stock_quantity + ? < 0 THEN 0 ELSE stock_quantity + ? END, "
                + "stock_base_quantity = CASE "
                + "  WHEN stock_base_quantity + ? < 0 THEN 0 "
                + "  ELSE stock_base_quantity + ? END "
                + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setDouble(2, delta);
            ps.setDouble(3, delta * factor);
            ps.setDouble(4, delta * factor);
            ps.setInt(5, ingredientId);
            ps.executeUpdate();
        }
    }

    private double fetchUnitFactor(Connection conn, int ingredientId) throws Exception {
        String sql = "SELECT unit, COALESCE(unit_factor, 1) AS unit_factor FROM ingredients WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double factor = rs.getDouble("unit_factor");
                    if (factor > 0) {
                        return factor;
                    }
                    StockUnit unit = StockUnit.fromDisplayUnit(rs.getString("unit"));
                    return unit.factorToBase();
                }
            }
        }
        return 1.0;
    }

    private Ingredient mapRow(ResultSet rs) throws Exception {
        return new Ingredient(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getString("unit_base"),
                rs.getDouble("unit_factor"),
                rs.getDouble("package_size"),
                rs.getDouble("package_price"),
                rs.getDouble("stock_quantity"),
                rs.getDouble("min_quantity"),
                rs.getDouble("stock_base_quantity"),
                rs.getDouble("min_base_quantity"),
                rs.getInt("active") == 1
        );
    }
}
