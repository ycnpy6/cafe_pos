package com.cafepos.model;

public record ProductIngredientUsage(
        int ingredientId,
        String ingredientName,
        String unit,
        double quantityPerProduct,
        double unitCost,
        double availableStock
) {
}
