package com.cafepos.model;

public record IngredientUsageRow(
        String name,
        String unit,
        double quantity,
        double totalCost
) {
}
