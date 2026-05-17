package com.cafepos.controllers;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.UserDAO;
import com.cafepos.hardware.PrinterService;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.PrintQueueService;
import com.cafepos.util.BackupService;
import com.cafepos.util.SecurityUtils;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SettingsController {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);
    private static final String PRINTER_KEY = "printer.name";
    private static final String STOCK_THRESHOLD_KEY = "stock.low.threshold";
    private static final int MAX_TOASTS = 3;

    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final PrinterService printerService = new PrinterService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();
    private final UserDAO userDAO = new UserDAO();
    private final BackupService backupService = new BackupService();

    private final ObservableList<User> users = FXCollections.observableArrayList();

    @FXML
    private ComboBox<String> printerBox;
    @FXML
    private Label printQueueStatusLabel;
    @FXML
    private TextField stockThresholdField;
    @FXML
    private TableView<User> usersTable;
    @FXML
    private TableColumn<User, String> userNameColumn;
    @FXML
    private TableColumn<User, String> userRoleColumn;
    @FXML
    private TextField newUserNameField;
    @FXML
    private PasswordField newUserPinField;
    @FXML
    private ComboBox<UserRole> newUserRoleBox;
    @FXML
    private Label backupStatusLabel;
    @FXML
    private VBox toastContainer;

    @FXML
    private void initialize() {
        configureUsersTable();
        newUserRoleBox.getItems().setAll(UserRole.values());
        newUserRoleBox.getSelectionModel().select(UserRole.BARISTA);
        loadPrinters();
        loadStockThreshold();
        loadUsers();
        refreshQueueStatus();
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
    private void onSavePrinter() {
        String value = printerBox.getSelectionModel().getSelectedItem();
        if (value == null || value.isBlank()) {
            showToast("warning", "Selectionnez une imprimante");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(PRINTER_KEY, value);
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Imprimante enregistree"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde", task.getException());
            showToast("error", "Sauvegarde impossible");
        });
        Thread thread = new Thread(task, "printer-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onTestPrint() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                printerService.printTestTicket();
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Test impression envoye"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur test impression", task.getException());
            showToast("error", "Test impression impossible");
        });
        Thread thread = new Thread(task, "printer-test");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadStockThreshold() {
        Task<Void> task = new Task<>() {
            private String value;

            @Override
            protected Void call() throws Exception {
                value = settingsDAO.getValue(STOCK_THRESHOLD_KEY);
                return null;
            }

            @Override
            protected void succeeded() {
                stockThresholdField.setText(value == null ? "" : value);
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur chargement seuil", task.getException()));
        Thread thread = new Thread(task, "stock-threshold-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSaveStockThreshold() {
        String value = stockThresholdField.getText();
        if (value == null || value.isBlank()) {
            showToast("warning", "Seuil invalide");
            return;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            showToast("warning", "Seuil invalide");
            return;
        }
        if (parsed < 0) {
            showToast("warning", "Seuil invalide");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(STOCK_THRESHOLD_KEY, String.valueOf(parsed));
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Seuil stock mis a jour"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde seuil", task.getException());
            showToast("error", "Sauvegarde impossible");
        });
        Thread thread = new Thread(task, "stock-threshold-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshQueueStatus() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return printQueueService.countPendingSafe();
            }
        };
        task.setOnSucceeded(evt -> {
            int count = task.getValue();
            printQueueStatusLabel.setText("File: " + count + " en attente");
        });
        Thread thread = new Thread(task, "print-queue-count");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDispatchQueue() {
        printQueueService.dispatchAsync();
        refreshQueueStatus();
        showToast("info", "Relance impression envoyee");
    }

    private void configureUsersTable() {
        usersTable.setItems(users);
        userNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        userRoleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole().name()));
    }

    private void loadUsers() {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                return userDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> users.setAll(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement users", task.getException());
            showToast("error", "Chargement utilisateurs impossible");
        });
        Thread thread = new Thread(task, "users-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onAddUser() {
        String name = newUserNameField.getText();
        String pin = newUserPinField.getText();
        UserRole role = newUserRoleBox.getValue();
        if (name == null || name.isBlank() || pin == null || pin.isBlank() || role == null) {
            showToast("warning", "Champs incomplets");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String hash = SecurityUtils.sha256Hex(pin.trim());
                userDAO.insertUser(name.trim(), hash, role);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            newUserNameField.clear();
            newUserPinField.clear();
            loadUsers();
            showToast("success", "Utilisateur ajoute");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout user", task.getException());
            showToast("error", "Ajout utilisateur impossible");
        });
        Thread thread = new Thread(task, "user-add");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDeleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showToast("warning", "Selectionnez un utilisateur");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (selected.getRole() == UserRole.MANAGER && userDAO.countByRole(UserRole.MANAGER) <= 1) {
                    throw new IllegalStateException("Dernier manager");
                }
                userDAO.deleteUser(selected.getId());
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            loadUsers();
            showToast("success", "Utilisateur supprime");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur suppression user", task.getException());
            showToast("warning", "Suppression refusee");
        });
        Thread thread = new Thread(task, "user-delete");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onBackupNow() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                backupService.runBackup();
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            backupStatusLabel.setText("Sauvegarde terminee");
            showToast("success", "Sauvegarde terminee");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde", task.getException());
            showToast("error", "Sauvegarde impossible");
        });
        Thread thread = new Thread(task, "backup-now");
        thread.setDaemon(true);
        thread.start();
    }

    private void showToast(String type, String message) {
        if (toastContainer == null) {
            return;
        }
        HBox toast = new HBox(8);
        toast.getStyleClass().add("toast");
        if (type != null && !type.isBlank()) {
            toast.getStyleClass().add(type);
        }
        Label icon = new Label(iconFor(type));
        Label text = new Label(message == null ? "" : message);
        text.setWrapText(true);
        toast.getChildren().addAll(icon, text);

        toastContainer.getChildren().add(0, toast);
        while (toastContainer.getChildren().size() > MAX_TOASTS) {
            toastContainer.getChildren().remove(toastContainer.getChildren().size() - 1);
        }

        PauseTransition delay = new PauseTransition(Duration.millis(3000));
        delay.setOnFinished(evt -> fadeOutToast(toast));
        delay.play();
    }

    private void fadeOutToast(HBox toast) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), toast);
        fade.setToValue(0);
        fade.setOnFinished(evt -> toastContainer.getChildren().remove(toast));
        fade.play();
    }

    private String iconFor(String type) {
        return switch (type == null ? "" : type) {
            case "success" -> "OK";
            case "error" -> "X";
            case "warning" -> "!";
            default -> "i";
        };
    }
}
