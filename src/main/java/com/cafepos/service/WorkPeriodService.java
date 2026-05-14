package com.cafepos.service;

import com.cafepos.dao.WorkPeriodDAO;
import com.cafepos.db.DatabaseManager;

public class WorkPeriodService {
    private final WorkPeriodDAO workPeriodDAO = new WorkPeriodDAO();

    public int openIfNeeded(int userId) throws Exception {
        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            Integer openId = workPeriodDAO.findOpenWorkPeriodId(conn);
            if (openId != null) {
                return openId;
            }
            return workPeriodDAO.openWorkPeriod(conn, userId);
        }
    }
}
