package com.cafepos.controllers;

import com.cafepos.service.ReportService;
import com.cafepos.util.FormatUtils;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class DashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);
    private final ReportService reportService = new ReportService();

    @FXML
    private Label salesLabel;
    @FXML
    private Label ordersLabel;

    @FXML
    private void initialize() {
        loadToday();
    }

    private void loadToday() {
        LocalDate today = LocalDate.now();
        Task<Void> task = new Task<>() {
            private double total;
            private int count;

            @Override
            protected Void call() throws Exception {
                total = reportService.getTotalSales(today, today);
                count = reportService.getOrderCount(today, today);
                return null;
            }

            @Override
            protected void succeeded() {
                salesLabel.setText("Ventes du jour: " + FormatUtils.formatMoney(total));
                ordersLabel.setText("Nombre de commandes: " + count);
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur dashboard", task.getException()));
        Thread thread = new Thread(task, "dashboard-load");
        thread.setDaemon(true);
        thread.start();
    }
}
