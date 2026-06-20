package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.RFIDDecoder;
import com.cafepos.model.Customer;

public class CustomerDAO {
    public Customer findById(int customerId) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return findById(conn, customerId);
        }
    }

    public Customer findById(Connection conn, int customerId) throws Exception {
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            rs.getInt("active") == 1
                    );
                }
            }
        }
        return null;
    }

    /** Extra contact and history fields not stored in the {@link Customer} model. */
    public record CustomerExtras(String phone, String email, String address,
                                 double lifetimeSpent, int visitCount, String lastVisitAt) {
        public static final CustomerExtras EMPTY = new CustomerExtras("", "", "", 0, 0, "");
    }

    /** Loads the optional contact / spend fields for a customer. */
    public CustomerExtras loadExtras(int customerId) throws Exception {
        String sql = "SELECT phone, email, address, lifetime_spent, visit_count, last_visit_at " +
                     "FROM customers WHERE id = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CustomerExtras(
                            nullToEmpty(rs.getString("phone")),
                            nullToEmpty(rs.getString("email")),
                            nullToEmpty(rs.getString("address")),
                            rs.getDouble("lifetime_spent"),
                            rs.getInt("visit_count"),
                            nullToEmpty(rs.getString("last_visit_at"))
                    );
                }
            }
        }
        return CustomerExtras.EMPTY;
    }

    /** Bulk loader for the list view (one query, all customers). */
    public java.util.Map<Integer, CustomerExtras> loadAllExtras() throws Exception {
        java.util.Map<Integer, CustomerExtras> map = new java.util.HashMap<>();
        String sql = "SELECT id, phone, email, address, lifetime_spent, visit_count, last_visit_at FROM customers";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt("id"), new CustomerExtras(
                        nullToEmpty(rs.getString("phone")),
                        nullToEmpty(rs.getString("email")),
                        nullToEmpty(rs.getString("address")),
                        rs.getDouble("lifetime_spent"),
                        rs.getInt("visit_count"),
                        nullToEmpty(rs.getString("last_visit_at"))
                ));
            }
        }
        return map;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public Customer findByCardUid(String cardUid) throws Exception {
        String normalizedUid = RFIDDecoder.normalize(cardUid);
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE UPPER(card_uid) = ? LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            rs.getInt("active") == 1
                    );
                }
            }
        }
        return null;
    }

    public Customer findActiveByCardUid(String cardUid) throws Exception {
        String normalizedUid = RFIDDecoder.normalize(cardUid);
        String sql = "SELECT id, name, card_uid, balance, active FROM customers WHERE UPPER(card_uid) = ? AND active = 1 LIMIT 1";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("card_uid"),
                            rs.getDouble("balance"),
                            true
                    );
                }
            }
        }
        return null;
    }

    public void updateBalance(Connection conn, int customerId, double newBalance) throws Exception {
        String sql = "UPDATE customers SET balance = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public List<Customer> findAll() throws Exception {
        String sql = "SELECT id, name, card_uid, balance, active FROM customers ORDER BY name";
        List<Customer> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new Customer(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("card_uid"),
                        rs.getDouble("balance"),
                        rs.getInt("active") == 1
                ));
            }
        }
        return results;
    }

    public int insertCustomer(String name, String cardUid, double balance) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return insertCustomer(conn, name, cardUid, balance);
        }
    }

    public int insertCustomer(String name, String cardUid, double balance, boolean active) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            return insertCustomer(conn, name, cardUid, balance, active);
        }
    }

    public int insertCustomer(Connection conn, String name, String cardUid, double balance) throws Exception {
        return insertCustomer(conn, name, cardUid, balance, true);
    }

    public int insertCustomer(Connection conn, String name, String cardUid, double balance, boolean active) throws Exception {
        String normalizedUid = (cardUid == null || cardUid.isBlank()) ? null : RFIDDecoder.normalize(cardUid);
        String sql = "INSERT INTO customers (name, card_uid, balance, active) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            if (normalizedUid == null || normalizedUid.isBlank()) {
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setString(2, normalizedUid);
            }
            ps.setDouble(3, balance);
            ps.setInt(4, active ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updatePhone(Connection conn, int customerId, String phone) throws Exception {
        if (phone == null || phone.isBlank()) return;
        String sql = "UPDATE customers SET phone = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone.trim());
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    /**
     * Updates optional contact + history fields on a customer record.
     * Pass null/blank to skip a given field (no overwrite).
     */
    public void updateExtraFields(Connection conn, int customerId,
                                  String phone, String email, String address,
                                  Double lifetimeSpent, Integer visitCount, String lastVisitAt) throws Exception {
        StringBuilder sb = new StringBuilder("UPDATE customers SET ");
        List<Object> params = new ArrayList<>();
        if (phone != null && !phone.isBlank())     { sb.append("phone = ?, ");          params.add(phone.trim()); }
        if (email != null && !email.isBlank())     { sb.append("email = ?, ");          params.add(email.trim()); }
        if (address != null && !address.isBlank()) { sb.append("address = ?, ");        params.add(address.trim()); }
        if (lifetimeSpent != null)                  { sb.append("lifetime_spent = ?, "); params.add(lifetimeSpent); }
        if (visitCount != null)                     { sb.append("visit_count = ?, ");    params.add(visitCount); }
        if (lastVisitAt != null && !lastVisitAt.isBlank()) {
            sb.append("last_visit_at = ?, "); params.add(lastVisitAt.trim());
        }
        if (params.isEmpty()) return;
        // strip trailing ", "
        sb.setLength(sb.length() - 2);
        sb.append(" WHERE id = ?");
        params.add(customerId);
        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    public void updateName(Connection conn, int customerId, String name) throws Exception {
        String sql = "UPDATE customers SET name = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateCardUid(Connection conn, int customerId, String cardUid) throws Exception {
        String normalizedUid = RFIDDecoder.normalize(cardUid);
        String sql = "UPDATE customers SET card_uid = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUid);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void updateActive(Connection conn, int customerId, boolean active) throws Exception {
        String sql = "UPDATE customers SET active = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public void deleteCustomer(Connection conn, int customerId) throws Exception {
        String sql = "DELETE FROM customers WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        }
    }
}
