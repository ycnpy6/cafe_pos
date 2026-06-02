package com.cafepos.test;

import com.cafepos.dao.IngredientDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.model.Ingredient;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.Product;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.service.OrderService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StockRecipeIntegrationTest {
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
    void recipeUnitConvertsCostForMilk() throws Exception {
        int ingredientId = ingredientDAO.insertIngredient(new Ingredient(
                0,
                "Lait",
                "L",
                1.0,
                170.0,
                10.0,
                0.0,
                true
        ));
        assertTrue(ingredientId > 0);

        int productId = productDAO.insertProduct(new Product(
                0,
                "Espresso",
                0.0,
                0.0,
                1,
                0,
                true,
                true
        ));
        assertTrue(productId > 0);

        productIngredientDAO.upsertRecipeLine(productId, ingredientId, 200.0, "ML");
        List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(productId);
        assertEquals(1, recipe.size());

        ProductIngredientUsage usage = recipe.get(0);
        assertEquals("ML", usage.unit());
        assertEquals(200.0, usage.quantityPerProduct(), 0.0001);
        assertEquals(0.17, usage.unitCost(), 0.001);
    }

    @Test
    void saleConsumesIngredientStockForPreparedProduct() throws Exception {
        int ingredientId = ingredientDAO.insertIngredient(new Ingredient(
                0,
                "Capsule Espresso",
                "UNIT",
                10.0,
                150.0,
                10.0,
                0.0,
                true
        ));
        assertTrue(ingredientId > 0);

        int productId = productDAO.insertProduct(new Product(
                0,
                "Espresso",
                20.0,
                0.0,
                1,
                0,
                true,
                true
        ));
        assertTrue(productId > 0);

        productIngredientDAO.upsertRecipeLine(productId, ingredientId, 1.0, "UNIT");

        Product product;
        try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
            product = productDAO.findById(conn, productId);
            assertNotNull(product);
        }

        Order order = new Order();
        order.addLine(new OrderLine(product, 1, List.of()));

        int orderId = orderService.saveOrder(order, PaymentType.ESPECES);
        assertTrue(orderId > 0);

        try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
            Ingredient updated = ingredientDAO.findById(conn, ingredientId);
            assertNotNull(updated);
            assertEquals(9.0, updated.getStockQuantity(), 0.0001);
        }
    }
}
