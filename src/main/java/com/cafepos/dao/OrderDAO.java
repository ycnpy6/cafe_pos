package com.cafepos.dao;

import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class OrderDAO {
    public int insertOrder(Connection conn, Order order, Integer userId, Integer workPeriodId) throws Exception {
        String sql = "INSERT INTO orders (customer_id, payment_type, total, user_id, work_period_id, cash_amount, prepaid_amount) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Customer customer = order.getCustomer();
            if (customer == null) {
                ps.setObject(1, null);
            } else {
                ps.setInt(1, customer.getId());
            }
            PaymentType type = order.getPaymentType();
            ps.setString(2, type == null ? PaymentType.ESPECES.name() : type.name());
            ps.setDouble(3, order.getTotal());
            if (userId == null) {
                ps.setObject(4, null);
            } else {
                ps.setInt(4, userId);
            }
            if (workPeriodId == null) {
                ps.setObject(5, null);
            } else {
                ps.setInt(5, workPeriodId);
            }
            ps.setDouble(6, order.getCashAmount());
            ps.setDouble(7, order.getPrepaidAmount());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int orderId = rs.getInt(1);
                    insertLines(conn, orderId, order);
                    return orderId;
                }
            }
        }
        return -1;
    }

    private void insertLines(Connection conn, int orderId, Order order) throws Exception {
        String sql = "INSERT INTO order_lines (order_id, product_id, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (OrderLine line : order.getLines()) {
                ps.setInt(1, orderId);
                ps.setInt(2, line.getProduct().getId());
                ps.setInt(3, line.getQuantity());
                ps.setDouble(4, line.getUnitTotal());
                ps.setDouble(5, line.getLineTotal());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int lineId = keys.getInt(1);
                        insertLineTags(conn, lineId, line.getTags());
                    }
                }
            }
        }
    }

    private void insertLineTags(Connection conn, int lineId, java.util.List<Tag> tags) throws Exception {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO order_line_tags (line_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Tag tag : tags) {
                ps.setInt(1, lineId);
                ps.setInt(2, tag.getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
