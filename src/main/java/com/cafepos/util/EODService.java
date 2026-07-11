package com.cafepos.util;

import com.cafepos.dao.WorkPeriodDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.service.DailyExportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EODService {
    private static final Logger LOG = LoggerFactory.getLogger(EODService.class);

    private final WorkPeriodDAO workPeriodDAO = new WorkPeriodDAO();
    private final DailyExportService dailyExportService = new DailyExportService();

    /**
     * Cloture les periodes de travail restees ouvertes depuis un jour
     * precedent (l'EOD planifie de 23h55 ne tourne que si l'app est ouverte)
     * et regenere les exports quotidiens des journees concernees.
     * Appele au demarrage, apres l'initialisation de la base.
     *
     * @return nombre de periodes rattrapees
     */
    public int runEodCatchUp() throws Exception {
        List<WorkPeriodDAO.StalePeriod> stale;
        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            stale = workPeriodDAO.findStaleOpenWorkPeriods(conn);
            for (WorkPeriodDAO.StalePeriod period : stale) {
                double total = workPeriodDAO.getTotalSalesByWorkPeriod(conn, period.id());
                int count = workPeriodDAO.getOrderCountByWorkPeriod(conn, period.id());
                workPeriodDAO.insertEodReport(conn, period.id(), total, count);
                workPeriodDAO.closeWorkPeriod(conn, period.id());
            }
            conn.commit();
        }

        // Exports apres commit : la cloture reste acquise meme si l'ecriture
        // des fichiers echoue (dossier reseau absent, disque plein...).
        Set<String> days = new LinkedHashSet<>();
        for (WorkPeriodDAO.StalePeriod period : stale) {
            if (period.openedDate() != null) {
                days.add(period.openedDate());
            }
        }
        for (String day : days) {
            exportDaySafe(LocalDate.parse(day));
        }
        return stale.size();
    }

    public void runEod() throws Exception {
        boolean closedSomething = false;
        try (java.sql.Connection conn = DatabaseManager.openConnection()) {
            conn.setAutoCommit(false);
            Integer openId = workPeriodDAO.findOpenWorkPeriodId(conn);
            if (openId == null) {
                conn.rollback();
            } else {
                double total = workPeriodDAO.getTotalSalesByWorkPeriod(conn, openId);
                int count = workPeriodDAO.getOrderCountByWorkPeriod(conn, openId);
                workPeriodDAO.insertEodReport(conn, openId, total, count);
                workPeriodDAO.closeWorkPeriod(conn, openId);
                conn.commit();
                closedSomething = true;
            }
        }
        // Le rapport du jour est genere meme sans periode ouverte : des ventes
        // ont pu etre encaissees puis la periode fermee manuellement.
        exportDaySafe(LocalDate.now());
        if (closedSomething) {
            LOG.info("EOD termine avec export quotidien");
        }
    }

    private void exportDaySafe(LocalDate day) {
        try {
            dailyExportService.exportDay(day);
        } catch (Exception ex) {
            LOG.error("Echec export quotidien {}", day, ex);
        }
    }
}
