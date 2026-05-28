package com.cafepos.dao;

import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.PosOrderSummary;
import com.cafepos.model.RefundableOrderLine;
import com.cafepos.model.Tag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    public List<PosOrderSummary> findRecentOrders(int limit) throws Exception {
        String sql = "SELECT o.id, o.created_at, o.total, o.payment_type, o.customer_id, "
                + "COALESCE(c.name, '') AS customer_name "
                + "FROM orders o "
                + "LEFT JOIN customers c ON c.id = o.customer_id "
                + "ORDER BY o.created_at DESC LIMIT ?";
        List<PosOrderSummary> results = new ArrayList<>();
        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(toSummary(rs));
                }
            }
        }
        return results;
    }

    public List<PosOrderSummary> findTodayOrders(int limit) throws Exception {
        String sql = "SELECT o.id, o.created_at, o.total, o.payment_type, o.customer_id, "
                + "COALESCE(c.name, '') AS customer_name "
                + "FROM orders o "
                + "LEFT JOIN customers c ON c.id = o.customer_id "
                + "WHERE date(o.created_at) = date('now') "
                + "ORDER BY o.created_at DESC LIMIT ?";
        List<PosOrderSummary> results = new ArrayList<>();
        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(toSummary(rs));
                }
            }
        }
        return results;
    }

    public List<PosOrderSummary> searchOrders(String query, int limit) throws Exception {
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        if (safeQuery.isBlank()) {
            return findRecentOrders(limit);
        }
        String like = "%" + safeQuery + "%";
        String sql = "SELECT o.id, o.created_at, o.total, o.payment_type, o.customer_id, "
                + "COALESCE(c.name, '') AS customer_name "
                + "FROM orders o "
                + "LEFT JOIN customers c ON c.id = o.customer_id "
                + "WHERE CAST(o.id AS TEXT) LIKE ? "
                + "OR lower(COALESCE(c.name, '')) LIKE ? "
                + "OR strftime('%d/%m/%Y %H:%M', o.created_at) LIKE ? "
                + "ORDER BY o.created_at DESC LIMIT ?";
        List<PosOrderSummary> results = new ArrayList<>();
        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(toSummary(rs));
                }
            }
        }
        return results;
    }

    public List<RefundableOrderLine> findRefundableLines(int orderId) throws Exception {
        try (Connection conn = com.cafepos.db.DatabaseManager.openConnection()) {
            return findRefundableLines(conn, orderId);
        }
    }

    public List<RefundableOrderLine> findRefundableLines(Connection conn, int orderId) throws Exception {
        String sql = "SELECT ol.id, ol.product_id, p.name AS product_name, ol.quantity AS sold_qty, "
                + "ol.unit_price, ol.line_total, "
                + "COALESCE((SELECT SUM(rl.quantity) FROM refund_lines rl WHERE rl.order_line_id = ol.id), 0) AS refunded_qty "
                + "FROM order_lines ol "
                + "JOIN products p ON p.id = ol.product_id "
                + "WHERE ol.order_id = ? "
                + "ORDER BY ol.id";
        List<RefundableOrderLine> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int soldQty = rs.getInt("sold_qty");
                    int refundedQty = rs.getInt("refunded_qty");
                    int refundableQty = Math.max(0, soldQty - refundedQty);
                    if (refundableQty <= 0) {
                        continue;
                    }
                    results.add(new RefundableOrderLine(
                            rs.getInt("id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            soldQty,
                            refundedQty,
                            refundableQty,
                            rs.getDouble("unit_price"),
                            rs.getDouble("line_total")
                    ));
                }
            }
        }
        return results;
    }

    public Integer findOrderCustomerId(Connection conn, int orderId) throws Exception {
        String sql = "SELECT customer_id FROM orders WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject("customer_id") == null) {
                    return null;
                }
                return rs.getInt("customer_id");
            }
        }
    }

    public int insertRefund(Connection conn, int originalOrderId, String reason, String refundMethod,
                            double total, Integer userId) throws Exception {
        String sql = "INSERT INTO refunds (original_order_id, reason, refund_method, total, user_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalOrderId);
            ps.setString(2, reason);
            ps.setString(3, refundMethod);
            ps.setDouble(4, total);
            if (userId == null) {
                ps.setObject(5, null);
            } else {
                ps.setInt(5, userId);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void insertRefundLine(Connection conn, int refundId, int orderLineId, int quantity, double lineTotal)
            throws Exception {
        String sql = "INSERT INTO refund_lines (refund_id, order_line_id, quantity, line_total) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, refundId);
            ps.setInt(2, orderLineId);
            ps.setInt(3, quantity);
            ps.setDouble(4, lineTotal);
            ps.executeUpdate();
        }
    }

    private PosOrderSummary toSummary(ResultSet rs) throws Exception {
        PaymentType paymentType = PaymentType.ESPECES;
        String rawPayment = rs.getString("payment_type");
        if (rawPayment != null && !rawPayment.isBlank()) {
            try {
                paymentType = PaymentType.valueOf(rawPayment);
            } catch (IllegalArgumentException ignored) {
                paymentType = PaymentType.ESPECES;
            }
        }
        Integer customerId = rs.getObject("customer_id") == null ? null : rs.getInt("customer_id");
        return new PosOrderSummary(
                rs.getInt("id"),
                rs.getString("created_at"),
                rs.getDouble("total"),
                paymentType,
                customerId,
                rs.getString("customer_name")
        );
    }
}
