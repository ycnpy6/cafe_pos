package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.StockUnit;
import com.cafepos.model.UnitType;

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
        String sql = "SELECT i.id AS ingredient_id, i.name AS ingredient_name, "
                + "i.unit AS ingredient_unit, "
                + "COALESCE(i.unit_base, i.unit) AS unit_base, "
                + "COALESCE(i.unit_factor, 1) AS unit_factor, "
                + "i.package_size, i.package_price, "
                + "i.stock_quantity, "
                + "pi.quantity, pi.unit AS recipe_unit, pi.quantity_base "
                + "FROM product_ingredients pi "
                + "JOIN ingredients i ON i.id = pi.ingredient_id "
                + "WHERE pi.product_id = ? "
                + "ORDER BY i.name";
        List<ProductIngredientUsage> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapUsage(rs));
                }
            }
        }
        return results;
    }

    public void upsertRecipeLine(int productId, int ingredientId, double quantity) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            upsertRecipeLine(conn, productId, ingredientId, quantity, null);
        }
    }

    public void upsertRecipeLine(int productId, int ingredientId, double quantity, String unit) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            upsertRecipeLine(conn, productId, ingredientId, quantity, unit);
        }
    }

    public void upsertRecipeLine(Connection conn, int productId, int ingredientId, double quantity, String unit)
            throws Exception {
        IngredientUnitInfo unitInfo = fetchIngredientUnitInfo(conn, ingredientId);
        String resolvedUnit = unit == null || unit.isBlank() ? unitInfo.unit : unit;
        StockUnit recipeUnit = StockUnit.fromDisplayUnit(resolvedUnit);
        if (unitInfo.unitBase != null && !unitInfo.unitBase.isBlank()
                && !recipeUnit.unitBase().equalsIgnoreCase(unitInfo.unitBase)) {
            resolvedUnit = unitInfo.unit;
            recipeUnit = StockUnit.fromDisplayUnit(resolvedUnit);
        }
        double quantityBase = quantity * recipeUnit.factorToBase();
        String sql = "INSERT INTO product_ingredients (product_id, ingredient_id, quantity, unit, quantity_base) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT(product_id, ingredient_id) DO UPDATE SET "
                + "quantity = excluded.quantity, unit = excluded.unit, quantity_base = excluded.quantity_base";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, ingredientId);
            ps.setDouble(3, quantity);
            ps.setString(4, resolvedUnit);
            ps.setDouble(5, quantityBase);
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

    private ProductIngredientUsage mapUsage(ResultSet rs) throws Exception {
        int ingredientId = rs.getInt("ingredient_id");
        String ingredientName = rs.getString("ingredient_name");
        String ingredientUnit = rs.getString("ingredient_unit");
        String unitBase = rs.getString("unit_base");
        double unitFactor = rs.getDouble("unit_factor");
        double packageSize = rs.getDouble("package_size");
        double packagePrice = rs.getDouble("package_price");
        double stockQuantity = rs.getDouble("stock_quantity");
        double quantity = rs.getDouble("quantity");
        double quantityBase = rs.getDouble("quantity_base");
        String recipeUnit = rs.getString("recipe_unit");

        String resolvedUnit = recipeUnit == null || recipeUnit.isBlank() ? ingredientUnit : recipeUnit;
        StockUnit ingredientUnitInfo = StockUnit.fromDisplayUnit(ingredientUnit);
        if (unitBase == null || unitBase.isBlank()) {
            unitBase = ingredientUnitInfo.unitBase();
        }
        double safeUnitFactor = unitFactor <= 0 ? ingredientUnitInfo.factorToBase() : unitFactor;

        StockUnit recipeUnitInfo = StockUnit.fromDisplayUnit(resolvedUnit);
        if (!recipeUnitInfo.unitBase().equalsIgnoreCase(unitBase)) {
            resolvedUnit = ingredientUnitInfo.unitDisplay();
            recipeUnitInfo = ingredientUnitInfo;
        }

        if (quantityBase <= 0) {
            quantityBase = quantity * recipeUnitInfo.factorToBase();
        }

        double costPerDisplay = packageSize <= 0 ? 0 : (packagePrice / packageSize);
        double costPerBase = costPerDisplay / safeUnitFactor;
        double unitCost = costPerBase * recipeUnitInfo.factorToBase();

        return new ProductIngredientUsage(
                ingredientId,
                ingredientName,
                ingredientUnit,
                resolvedUnit,
                unitBase,
                safeUnitFactor,
            costPerDisplay,
                quantity,
                quantityBase,
                costPerBase,
                unitCost,
                stockQuantity
        );
    }

    private IngredientUnitInfo fetchIngredientUnitInfo(Connection conn, int ingredientId) throws Exception {
        String sql = "SELECT unit, COALESCE(unit_base, unit) AS unit_base FROM ingredients WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new IngredientUnitInfo(
                            rs.getString("unit"),
                            rs.getString("unit_base")
                    );
                }
            }
        }
        return new IngredientUnitInfo("UNIT", "UNIT");
    }

    private record IngredientUnitInfo(String unit, String unitBase) {
    }
}
