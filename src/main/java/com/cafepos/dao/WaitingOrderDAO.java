package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.Product;
import com.cafepos.model.Tag;
import com.cafepos.model.WaitingOrderSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class WaitingOrderDAO {
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public int save(Order order) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            try {
                int waitingOrderId = insertWaitingOrder(conn, order);
                for (OrderLine line : order.getLines()) {
                    int waitingLineId = insertWaitingLine(conn, waitingOrderId, line);
                    if (line.getTags() != null) {
                        for (Tag tag : line.getTags()) {
                            insertWaitingTag(conn, waitingLineId, tag.getId());
                        }
                    }
                }
                conn.commit();
                return waitingOrderId;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    public List<WaitingOrderSummary> findAll() throws Exception {
        String sql = """
                SELECT wo.id,
                       wo.customer_name,
                       wo.discount_percent,
                       wo.discount_amount,
                       wo.tva_percent,
                       wo.created_at,
                       (SELECT COUNT(*)
                          FROM waiting_order_lines wol
                         WHERE wol.waiting_order_id = wo.id) AS line_count,
                       COALESCE((
                           SELECT SUM((p.price + COALESCE(tags_sum.tag_total, 0)) * wol.quantity)
                             FROM waiting_order_lines wol
                             JOIN products p ON p.id = wol.product_id
                        LEFT JOIN (
                                   SELECT wlt.waiting_line_id,
                                          SUM(t.price_modifier) AS tag_total
                                     FROM waiting_order_line_tags wlt
                                     JOIN tags t ON t.id = wlt.tag_id
                                 GROUP BY wlt.waiting_line_id
                                 ) tags_sum ON tags_sum.waiting_line_id = wol.id
                            WHERE wol.waiting_order_id = wo.id
                       ), 0) AS subtotal
                  FROM waiting_orders wo
              ORDER BY wo.id DESC
                """;

        List<WaitingOrderSummary> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double subtotal = rs.getDouble("subtotal");
                double discountPercent = rs.getDouble("discount_percent");
                double discountAmount = rs.getDouble("discount_amount");
                double tvaPercent = rs.getDouble("tva_percent");

                double discount = discountPercent > 0
                        ? subtotal * (discountPercent / 100.0)
                        : Math.min(subtotal, Math.max(0, discountAmount));
                double net = Math.max(0, subtotal - discount);
                double total = net + net * (Math.max(0, tvaPercent) / 100.0);

                String customerName = rs.getString("customer_name");
                if (customerName == null || customerName.isBlank()) {
                    customerName = "Sans client";
                }

                results.add(new WaitingOrderSummary(
                        rs.getInt("id"),
                        customerName,
                        total,
                        rs.getInt("line_count"),
                        rs.getString("created_at")
                ));
            }
        }
        return results;
    }

    public Order load(int waitingOrderId) throws Exception {
        String orderSql = """
                SELECT id,
                       customer_id,
                       customer_name,
                       customer_card_uid,
                       discount_percent,
                       discount_amount,
                       tva_percent
                  FROM waiting_orders
                 WHERE id = ?
                 LIMIT 1
                """;

        try (Connection conn = DatabaseManager.openConnection()) {
            Order order = new Order();

            Integer customerId;
            String customerName;
            String customerCardUid;

            try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
                ps.setInt(1, waitingOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    int rawCustomerId = rs.getInt("customer_id");
                    customerId = rs.wasNull() ? null : rawCustomerId;
                    customerName = rs.getString("customer_name");
                    customerCardUid = rs.getString("customer_card_uid");
                    order.setDiscountPercent(rs.getDouble("discount_percent"));
                    if (order.getDiscountPercent() <= 0) {
                        order.setDiscountAmount(rs.getDouble("discount_amount"));
                    }
                    order.setTvaPercent(rs.getDouble("tva_percent"));
                }
            }

            String lineSql = """
                    SELECT id, product_id, quantity
                      FROM waiting_order_lines
                     WHERE waiting_order_id = ?
                  ORDER BY id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(lineSql)) {
                ps.setInt(1, waitingOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int lineId = rs.getInt("id");
                        int productId = rs.getInt("product_id");
                        int quantity = rs.getInt("quantity");

                        Product product = productDAO.findById(conn, productId);
                        if (product == null || quantity <= 0) {
                            continue;
                        }
                        List<Tag> tags = loadLineTags(conn, lineId);
                        order.addLine(new OrderLine(product, quantity, tags));
                    }
                }
            }

            if (customerId != null) {
                Customer customer = customerDAO.findById(conn, customerId);
                if (customer != null) {
                    order.setCustomer(customer);
                }
            }

            if (order.getCustomer() == null && customerCardUid != null && !customerCardUid.isBlank()) {
                String safeName = customerName == null || customerName.isBlank() ? "Client" : customerName;
                order.setCustomer(new Customer(customerId == null ? -1 : customerId, safeName, customerCardUid, 0));
            }

            return order;
        }
    }

    public void delete(int waitingOrderId) throws Exception {
        String sql = "DELETE FROM waiting_orders WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, waitingOrderId);
            ps.executeUpdate();
        }
    }

    public int count() throws Exception {
        String sql = "SELECT COUNT(*) FROM waiting_orders";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private int insertWaitingOrder(Connection conn, Order order) throws Exception {
        String sql = """
                INSERT INTO waiting_orders (customer_id, customer_name, customer_card_uid, discount_percent, discount_amount, tva_percent)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (order.getCustomer() == null) {
                ps.setObject(1, null);
                ps.setObject(2, null);
                ps.setObject(3, null);
            } else {
                ps.setInt(1, order.getCustomer().getId());
                ps.setString(2, order.getCustomer().getName());
                ps.setString(3, order.getCustomer().getCardUid());
            }
            ps.setDouble(4, order.getDiscountPercent());
            ps.setDouble(5, order.getDiscountAmount());
            ps.setDouble(6, order.getTvaPercent());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private int insertWaitingLine(Connection conn, int waitingOrderId, OrderLine line) throws Exception {
        String sql = """
                INSERT INTO waiting_order_lines (waiting_order_id, product_id, quantity)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, waitingOrderId);
            ps.setInt(2, line.getProduct().getId());
            ps.setInt(3, line.getQuantity());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private void insertWaitingTag(Connection conn, int waitingLineId, int tagId) throws Exception {
        String sql = "INSERT INTO waiting_order_line_tags (waiting_line_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, waitingLineId);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    private List<Tag> loadLineTags(Connection conn, int waitingLineId) throws Exception {
        String sql = """
                SELECT t.id, t.group_id, t.name, t.price_modifier
                  FROM waiting_order_line_tags wlt
                  JOIN tags t ON t.id = wlt.tag_id
                 WHERE wlt.waiting_line_id = ?
              ORDER BY wlt.id
                """;
        List<Tag> tags = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, waitingLineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(new Tag(
                            rs.getInt("id"),
                            rs.getInt("group_id"),
                            rs.getString("name"),
                            rs.getDouble("price_modifier")
                    ));
                }
            }
        }
        return tags;
    }
}
