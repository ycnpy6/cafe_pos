package com.cafepos.model;

public record ProductIngredientUsage(
        int ingredientId,
        String ingredientName,
        String ingredientUnit,
        String unit,
        String unitBase,
        double unitFactor,
        double ingredientUnitCost,
        double quantityPerProduct,
        double quantityBase,
        double costPerBase,
        double unitCost,
        double availableStock
) {
}
