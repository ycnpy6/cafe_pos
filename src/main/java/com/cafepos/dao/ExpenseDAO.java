package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class ExpenseDAO {
    public void insert(Connection conn,
                       String type,
                       String description,
                       double amount,
                       Integer workPeriodId) throws Exception {
        String sql = "INSERT INTO expenses (type, description, amount, work_period_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, description);
            ps.setDouble(3, amount);
            if (workPeriodId == null) {
                ps.setObject(4, null);
            } else {
                ps.setInt(4, workPeriodId);
            }
            ps.executeUpdate();
        }
    }
}
