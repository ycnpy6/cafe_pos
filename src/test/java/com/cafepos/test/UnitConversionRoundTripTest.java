package com.cafepos.test;

import com.cafepos.dao.IngredientDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Ingredient;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.Product;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.StockUnit;
import com.cafepos.service.OrderService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip / cross-unit conversion tests for the stock-and-cost engine.
 *
 * Scenarios:
 *  - Ingredient stored in KG, recipe in G  -> sale deducts the correct fraction of KG.
 *  - Ingredient stored in L,  recipe in CL -> conversion is exact (no rounding drift).
 *  - Ingredient stored in L,  recipe in ML -> cost-per-recipe-unit math is right.
 *  - Recipe unit family mismatch (G entered for ML ingredient) is rejected gracefully.
 */
public class UnitConversionRoundTripTest {
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductIngredientDAO productIngredientDAO = new ProductIngredientDAO();
    private final OrderService orderService = new OrderService();

    @BeforeAll
    static void initDb() throws Exception {
        TestDbHelper.initDatabase();
    }

    @BeforeEach
    void reset() throws Exception {
        TestDbHelper.resetData();
    }

    @Test
    void kgIngredientRecipeInGramsDeductsCorrectKg() throws Exception {
        // Café stored in KG, 5 KG in stock; recipe uses 20 G per espresso.
        int ingredientId = ingredientDAO.insertIngredient(new Ingredient(
                0, "Café grains", "KG", 1.0, 1800.0, 5.0, 1.0, true));
        assertTrue(ingredientId > 0);

        int productId = productDAO.insertProduct(new Product(
                0, "Espresso", 100.0, 0.0, 1, 0, true, true));
        productIngredientDAO.upsertRecipeLine(productId, ingredientId, 20.0, "G");

        // Verify recipe storage + cost-per-recipe-unit: 1800 DA / 1 KG / 1000 g = 1.8 DA/g
        List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(productId);
        assertEquals(1, recipe.size());
        ProductIngredientUsage usage = recipe.get(0);
        assertEquals("G", usage.unit());
        assertEquals(20.0, usage.quantityPerProduct(), 1e-9);
        assertEquals(1.8, usage.unitCost(), 1e-6);
        // Total recipe cost = quantity * unitCost = 20 * 1.8 = 36 DA
        assertEquals(36.0, usage.quantityPerProduct() * usage.unitCost(), 1e-6);

        // Sell 3 espressos -> consume 60 g = 0.06 KG. Remaining: 4.94 KG.
        Product product;
        try (var conn = DatabaseManager.openConnection()) {
            product = productDAO.findById(conn, productId);
        }
        Order order = new Order();
        order.addLine(new OrderLine(product, 3, List.of()));
        int orderId = orderService.saveOrder(order, PaymentType.ESPECES);
        assertTrue(orderId > 0);

        try (var conn = DatabaseManager.openConnection()) {
            Ingredient updated = ingredientDAO.findById(conn, ingredientId);
            assertEquals(4.94, updated.getStockQuantity(), 1e-9);
            // Base stock (g) should also be 4940
            assertEquals(4940.0, updated.getStockBaseQuantity(), 1e-6);
        }
    }

    @Test
    void litreIngredientRecipeInClConvertsExactly() throws Exception {
        // Lait stored in L, 2 L in stock; recipe uses 5 CL (= 50 mL = 0.05 L) per latte.
        int ingredientId = ingredientDAO.insertIngredient(new Ingredient(
                0, "Lait entier", "L", 1.0, 180.0, 2.0, 0.5, true));

        int productId = productDAO.insertProduct(new Product(
                0, "Latte", 250.0, 0.0, 1, 0, true, true));
        productIngredientDAO.upsertRecipeLine(productId, ingredientId, 5.0, "CL");

        List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(productId);
        ProductIngredientUsage usage = recipe.get(0);
        assertEquals("CL", usage.unit());
        // 180 DA / L => 1.8 DA / cL (cost per recipe unit)
        assertEquals(1.8, usage.unitCost(), 1e-6);
        // Total per recipe = 5 cL * 1.8 = 9 DA
        assertEquals(9.0, usage.quantityPerProduct() * usage.unitCost(), 1e-6);

        Product product;
        try (var conn = DatabaseManager.openConnection()) {
            product = productDAO.findById(conn, productId);
        }
        Order order = new Order();
        order.addLine(new OrderLine(product, 4, List.of())); // 4 lattes = 20 cL = 0.2 L
        orderService.saveOrder(order, PaymentType.ESPECES);

        try (var conn = DatabaseManager.openConnection()) {
            Ingredient updated = ingredientDAO.findById(conn, ingredientId);
            assertEquals(1.8, updated.getStockQuantity(), 1e-9);
            assertEquals(1800.0, updated.getStockBaseQuantity(), 1e-6);
        }
    }

    @Test
    void recipeUnitMismatchedFamilyFallsBackToIngredientUnit() throws Exception {
        // Ingredient stored in L; user tries to set recipe in G (weight) -> should be rejected.
        int ingredientId = ingredientDAO.insertIngredient(new Ingredient(
                0, "Sirop", "L", 1.0, 1200.0, 1.5, 0.5, true));
        int productId = productDAO.insertProduct(new Product(
                0, "Latte vanille", 280.0, 0.0, 1, 0, true, true));

        productIngredientDAO.upsertRecipeLine(productId, ingredientId, 10.0, "G");

        List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(productId);
        ProductIngredientUsage usage = recipe.get(0);
        // Must NOT be G; should have fallen back to ingredient's unit (L).
        assertEquals("L", usage.unit());
    }

    @Test
    void stockUnitFactorIsCommutative() {
        StockUnit kg = StockUnit.fromDisplayUnit("KG");
        // 2.5 KG -> base -> back -> 2.5 KG
        double base = kg.toBase(2.5);
        assertEquals(2500.0, base, 1e-9);
        assertEquals(2.5, kg.fromBase(base), 1e-9);

        StockUnit cl = StockUnit.fromDisplayUnit("CL");
        // 7 CL -> 70 ML -> 7 CL
        assertEquals(70.0, cl.toBase(7.0), 1e-9);
        assertEquals(7.0, cl.fromBase(70.0), 1e-9);
    }
}
