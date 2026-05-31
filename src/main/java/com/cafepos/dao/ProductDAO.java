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
        String sql = "SELECT id, name, price, cost, category_id, stock, active, is_prepared " +
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
                            rs.getInt("active") == 1,
                            rs.getInt("is_prepared") == 1
                    ));
                }
            }
        }
        return results;
    }

    public List<Product> findAll() throws Exception {
        String sql = "SELECT id, name, price, cost, category_id, stock, active, is_prepared FROM products ORDER BY name";
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
                        rs.getInt("active") == 1,
                        rs.getInt("is_prepared") == 1
                ));
            }
        }
        return results;
    }

    public int insertProduct(Product product) throws Exception {
        String sql = "INSERT INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getName());
            ps.setDouble(2, product.getPrice());
            ps.setDouble(3, product.getCost());
            ps.setInt(4, product.getCategoryId());
            ps.setInt(5, product.getStock());
            ps.setInt(6, product.isActive() ? 1 : 0);
            ps.setInt(7, product.isPrepared() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public Product findById(Connection conn, int productId) throws Exception {
        String sql = "SELECT id, name, price, cost, category_id, stock, active, is_prepared FROM products WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getDouble("cost"),
                            rs.getInt("category_id"),
                            rs.getInt("stock"),
                            rs.getInt("active") == 1,
                            rs.getInt("is_prepared") == 1
                    );
                }
            }
        }
        return null;
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

    public void updateName(int productId, String name) throws Exception {
        String sql = "UPDATE products SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public void updateCategory(int productId, int categoryId) throws Exception {
        String sql = "UPDATE products SET category_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public void updateActive(int productId, boolean active) throws Exception {
        String sql = "UPDATE products SET active = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public void updatePrepared(int productId, boolean prepared) throws Exception {
        String sql = "UPDATE products SET is_prepared = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, prepared ? 1 : 0);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public void updateCost(int productId, double cost) throws Exception {
        String sql = "UPDATE products SET cost = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, cost);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public void updatePriceWithHistory(int productId, double newPrice, Integer userId) throws Exception {
        String select = "SELECT price FROM products WHERE id = ?";
        String update = "UPDATE products SET price = ? WHERE id = ?";
        String insert = "INSERT INTO price_history (product_id, old_price, new_price, changed_by) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            double oldPrice = 0;
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setInt(1, productId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        oldPrice = rs.getDouble("price");
                    }
                }
            }
            if (Math.abs(oldPrice - newPrice) < 0.0001) {
                conn.rollback();
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setDouble(1, newPrice);
                ps.setInt(2, productId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, productId);
                ps.setDouble(2, oldPrice);
                ps.setDouble(3, newPrice);
                if (userId == null) {
                    ps.setObject(4, null);
                } else {
                    ps.setInt(4, userId);
                }
                ps.executeUpdate();
            }
            conn.commit();
        }
    }

    public int countByCategory(int categoryId) throws Exception {
        String sql = "SELECT COUNT(*) AS total FROM products WHERE category_id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        }
        return 0;
    }
}
