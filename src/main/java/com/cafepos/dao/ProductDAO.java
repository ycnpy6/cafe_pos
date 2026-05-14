package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {
    public List<Product> findActiveByCategory(int categoryId) throws Exception {
        String sql = "SELECT id, name, price, cost, category_id, stock, active " +
                "FROM products WHERE active = 1 AND category_id = ? ORDER BY name";
        List<Product> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getDouble("cost"),
                            rs.getInt("category_id"),
                            rs.getInt("stock"),
                            rs.getInt("active") == 1
                    ));
                }
            }
        }
        return results;
    }

    public List<Product> findAll() throws Exception {
        String sql = "SELECT id, name, price, cost, category_id, stock, active FROM products ORDER BY name";
        List<Product> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getDouble("cost"),
                        rs.getInt("category_id"),
                        rs.getInt("stock"),
                        rs.getInt("active") == 1
                ));
            }
        }
        return results;
    }

    public int insertProduct(Product product) throws Exception {
        String sql = "INSERT INTO products (name, price, cost, category_id, stock, active) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getName());
            ps.setDouble(2, product.getPrice());
            ps.setDouble(3, product.getCost());
            ps.setInt(4, product.getCategoryId());
            ps.setInt(5, product.getStock());
            ps.setInt(6, product.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public int getStockById(Connection conn, int productId) throws Exception {
        String sql = "SELECT stock FROM products WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock");
                }
            }
        }
        return 0;
    }

    public void decrementStock(Connection conn, int productId, int quantity) throws Exception {
        String sql = "UPDATE products SET stock = CASE WHEN stock - ? < 0 THEN 0 ELSE stock - ? END " +
                "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, quantity);
            ps.setInt(3, productId);
            ps.executeUpdate();
        }
    }

    public void adjustStock(Connection conn, int productId, int delta) throws Exception {
        String sql = "UPDATE products SET stock = CASE WHEN stock + ? < 0 THEN 0 ELSE stock + ? END " +
                "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, delta);
            ps.setInt(3, productId);
            ps.executeUpdate();
        }
    }
}
