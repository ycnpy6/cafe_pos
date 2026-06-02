package com.cafepos.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CashWithdrawalDAO {
    public void insert(Connection conn,
                       String reason,
                       double amount,
                       Integer userId,
                       Integer workPeriodId) throws Exception {
        String sql = "INSERT INTO cash_withdrawals (reason, amount, user_id, work_period_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setDouble(2, amount);
            if (userId == null) {
                ps.setObject(3, null);
            } else {
                ps.setInt(3, userId);
            }
            if (workPeriodId == null) {
                ps.setObject(4, null);
            } else {
                ps.setInt(4, workPeriodId);
            }
            ps.executeUpdate();
        }
    }
}
