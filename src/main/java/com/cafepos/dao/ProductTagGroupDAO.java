package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ProductTagGroupDAO {
    public List<Integer> findGroupIdsForProduct(int productId) throws Exception {
        String sql = "SELECT group_id FROM product_tag_groups WHERE product_id = ?";
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("group_id"));
                }
            }
        }
        return ids;
    }

    public void setGroupsForProduct(int productId, List<Integer> groupIds) throws Exception {
        try (Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            setGroupsForProduct(conn, productId, groupIds);
            conn.commit();
        }
    }
    public void setGroupsForProduct(Connection conn, int productId, List<Integer> groupIds) throws Exception {
        try (PreparedStatement clear = conn.prepareStatement(
                "DELETE FROM product_tag_groups WHERE product_id = ?")) {
            clear.setInt(1, productId);
            clear.executeUpdate();
        }
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO product_tag_groups (product_id, group_id) VALUES (?, ?)")) {
            for (Integer groupId : groupIds) {
                insert.setInt(1, productId);
                insert.setInt(2, groupId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
