package com.cafepos.model;

public record OrderLineDetail(String productName, int quantity, double lineTotal, String tags) {
}
