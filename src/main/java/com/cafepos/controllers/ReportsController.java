package com.cafepos.controllers;

import com.cafepos.model.ReportRow;
import com.cafepos.service.ReportService;
import com.cafepos.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class ReportsController {
    private static final Logger LOG = LoggerFactory.getLogger(ReportsController.class);
    private final ReportService reportService = new ReportService();

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private Label summaryLabel;
    @FXML
    private TableView<ReportRow> reportTable;
    @FXML
    private TableColumn<ReportRow, String> idColumn;
    @FXML
    private TableColumn<ReportRow, String> dateColumn;
    @FXML
    private TableColumn<ReportRow, String> paymentColumn;
    @FXML
    private TableColumn<ReportRow, String> totalColumn;

    @FXML
    private void initialize() {
        configureTable();
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today);
        endDatePicker.setValue(today);
        loadReport();
    }

    private void configureTable() {
        idColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getOrderId())));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCreatedAt()));
        paymentColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPaymentType().name()));
        totalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().getTotal())));
    }

    @FXML
    private void onLoad() {
        loadReport();
    }

    private void loadReport() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) {
            return;
        }
        Task<Void> task = new Task<>() {
            private List<ReportRow> rows;
            private double total;
            private int count;

            @Override
            protected Void call() throws Exception {
                rows = reportService.getOrders(start, end);
                total = reportService.getTotalSales(start, end);
                count = reportService.getOrderCount(start, end);
                return null;
            }

            @Override
            protected void succeeded() {
                reportTable.getItems().setAll(rows);
                summaryLabel.setText("Total: " + FormatUtils.formatMoney(total) + " / Commandes: " + count);
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur rapport", task.getException()));
        Thread thread = new Thread(task, "report-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onExport() {
        if (reportTable.getItems().isEmpty()) {
            showAlert("Aucune donnee", "Rien a exporter.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        Path path = null;
        if (reportTable.getScene() != null) {
            java.io.File file = chooser.showSaveDialog(reportTable.getScene().getWindow());
            if (file != null) {
                path = file.toPath();
            }
        }
        if (path == null) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id;date;paiement;total\n");
            for (ReportRow row : reportTable.getItems()) {
                writer.write(row.getOrderId() + ";" + row.getCreatedAt() + ";" + row.getPaymentType() + ";" + row.getTotal());
                writer.write("\n");
            }
        } catch (Exception ex) {
            LOG.error("Erreur export", ex);
            showAlert("Erreur", "Export impossible.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
