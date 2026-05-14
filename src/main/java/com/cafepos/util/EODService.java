package com.cafepos.util;

import com.cafepos.dao.WorkPeriodDAO;
import com.cafepos.db.DatabaseManager;

public class EODService {
    private final WorkPeriodDAO workPeriodDAO = new WorkPeriodDAO();

    public void runEod() throws Exception {
        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            Integer openId = workPeriodDAO.findOpenWorkPeriodId(conn);
            if (openId == null) {
                conn.rollback();
                return;
            }
            double total = workPeriodDAO.getTotalSalesByWorkPeriod(conn, openId);
            int count = workPeriodDAO.getOrderCountByWorkPeriod(conn, openId);
            workPeriodDAO.insertEodReport(conn, openId, total, count);
            workPeriodDAO.closeWorkPeriod(conn, openId);
            conn.commit();
        }
    }
}
