package com.cafepos.controllers;

import com.cafepos.service.ReportService;
import com.cafepos.model.AppAction;
import com.cafepos.util.ActionAccessManager;
import com.cafepos.util.FormatUtils;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class DashboardController {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);
    private final ReportService reportService = new ReportService();
    private final ActionAccessManager accessManager = new ActionAccessManager();

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

    @FXML
    private void onShortcutPos() {
        BackOfficeController controller = BackOfficeController.getCurrent();
        if (controller != null) {
            controller.goBackToPos();
        }
    }

    @FXML
    private void onShortcutStock() {
        navigateTo(AppAction.OPEN_STOCK, "/com/cafepos/fxml/stock.fxml");
    }

    @FXML
    private void onShortcutClients() {
        navigateTo(AppAction.OPEN_CLIENTS, "/com/cafepos/fxml/clients.fxml");
    }

    @FXML
    private void onShortcutReports() {
        navigateTo(AppAction.OPEN_REPORTS, "/com/cafepos/fxml/reports.fxml");
    }

    @FXML
    private void onShortcutSettings() {
        navigateTo(AppAction.OPEN_SETTINGS, "/com/cafepos/fxml/settings.fxml");
    }

    @FXML
    private void onShortcutBackToPos() {
        BackOfficeController controller = BackOfficeController.getCurrent();
        if (controller != null) {
            controller.goBackToPos();
        }
    }

    private void navigateTo(AppAction action, String fxml) {
        BackOfficeController controller = BackOfficeController.getCurrent();
        if (controller == null) {
            return;
        }
        if (!accessManager.ensureAccess(action, currentWindow())) {
            return;
        }
        controller.navigateTo(fxml);
    }

    private Stage currentWindow() {
        return salesLabel == null || salesLabel.getScene() == null
                ? null
                : (Stage) salesLabel.getScene().getWindow();
    }
}
