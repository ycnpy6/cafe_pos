package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.PrintQueueItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PrintQueueDAO {
    public void insert(Connection conn, int orderId, String ticketType, String payload) throws Exception {
        String sql = "INSERT INTO print_queue (order_id, ticket_type, payload, status, attempts) "
                + "VALUES (?, ?, ?, 'PENDING', 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, ticketType);
            ps.setString(3, payload);
            ps.executeUpdate();
        }
    }

    public List<PrintQueueItem> findPending(int limit) throws Exception {
        String sql = "SELECT id, order_id, ticket_type, payload, attempts FROM print_queue "
                + "WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT ?";
        List<PrintQueueItem> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new PrintQueueItem(
                            rs.getLong("id"),
                            rs.getInt("order_id"),
                            rs.getString("ticket_type"),
                            rs.getString("payload"),
                            rs.getInt("attempts")
                    ));
                }
            }
        }
        return results;
    }

    public void markPrinted(long id) throws Exception {
        String sql = "UPDATE print_queue SET status = 'PRINTED', printed_at = datetime('now') WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void incrementAttempts(long id) throws Exception {
        String sql = "UPDATE print_queue SET attempts = attempts + 1 WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int countPending() throws Exception {
        String sql = "SELECT COUNT(*) AS total FROM print_queue WHERE status = 'PENDING'";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
        }
        return 0;
    }

    public String findLatestPayload() throws Exception {
        String sql = "SELECT payload FROM print_queue ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("payload");
            }
        }
        return null;
    }

    public PrintQueueItem findLatestItem() throws Exception {
        String sql = "SELECT id, order_id, ticket_type, payload, attempts FROM print_queue "
                + "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new PrintQueueItem(
                        rs.getLong("id"),
                        rs.getInt("order_id"),
                        rs.getString("ticket_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")
                );
            }
        }
        return null;
    }

    public String findLatestPayloadByOrder(int orderId) throws Exception {
        String sql = "SELECT payload FROM print_queue WHERE order_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("payload");
                }
            }
        }
        return null;
    }

    public String findLatestPayloadByOrderAndType(int orderId, String ticketType) throws Exception {
        String sql = "SELECT payload FROM print_queue WHERE order_id = ? AND ticket_type = ? "
                + "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, ticketType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("payload");
                }
            }
        }
        return null;
    }
}
