package com.cafepos.model;

public record IngredientMovementSummaryRow(
        String name,
        String unit,
        double inflow,
        double outflow,
        double net,
        double totalCost
) {
}
