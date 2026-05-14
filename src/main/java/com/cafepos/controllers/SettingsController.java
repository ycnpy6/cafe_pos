package com.cafepos.controllers;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.hardware.PrinterService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SettingsController {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);
    private static final String PRINTER_KEY = "printer.name";

    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final PrinterService printerService = new PrinterService();

    @FXML
    private ComboBox<String> printerBox;

    @FXML
    private void initialize() {
        loadPrinters();
    }

    private void loadPrinters() {
        Task<Void> task = new Task<>() {
            private List<String> names;
            private String saved;

            @Override
            protected Void call() throws Exception {
                names = printerService.getPrinterNames();
                saved = settingsDAO.getValue(PRINTER_KEY);
                return null;
            }

            @Override
            protected void succeeded() {
                printerBox.getItems().setAll(names);
                if (saved != null) {
                    printerBox.getSelectionModel().select(saved);
                }
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur chargement imprimantes", task.getException()));
        Thread thread = new Thread(task, "printer-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSave() {
        String value = printerBox.getSelectionModel().getSelectedItem();
        if (value == null || value.isBlank()) {
            showAlert("Imprimante requise", "Selectionnez une imprimante.");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(PRINTER_KEY, value);
                return null;
            }
        };
        task.setOnSucceeded(evt -> showAlert("OK", "Parametres enregistres."));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde", task.getException());
            showAlert("Erreur", "Sauvegarde impossible.");
        });
        Thread thread = new Thread(task, "printer-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
