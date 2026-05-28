package com.cafepos.model;

public record SalesSummary(
	double total,
	int orderCount,
	double cashTotal,
	double prepaidTotal,
	double ingredientCost,
	double grossProfit
) {
}
