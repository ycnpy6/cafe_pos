package com.cafepos.model;

import java.util.Collections;
import java.util.List;

public class OrderLine {
    private final Product product;
    private int quantity;
    private final List<Tag> tags;

    public OrderLine(Product product, int quantity, List<Tag> tags) {
        this.product = product;
        this.quantity = quantity;
        this.tags = tags == null ? Collections.emptyList() : tags;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public double getUnitTotal() {
        double total = product.getPrice();
        for (Tag tag : tags) {
            total += tag.getPriceModifier();
        }
        return total;
    }

    public double getLineTotal() {
        return getUnitTotal() * quantity;
    }
}
