package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.AppAction;
import com.cafepos.hardware.PrinterService;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.PrintQueueService;
import com.cafepos.util.BackupService;
import com.cafepos.util.SecurityUtils;
import com.cafepos.util.UiIconHelper;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class SettingsController {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);

    private static final String PRINTER_KEY = "printer.name";
    private static final String STOCK_THRESHOLD_KEY = "stock.low.threshold";
    private static final String TVA_PERCENT_KEY = "tva_percent";
    private static final String BACKUP_TARGET_DIR_KEY = "backup.target.dir";
    private static final String EXPORT_DIR_KEY = "export.default.dir";
    private static final String APP_LANGUAGE_KEY = "app.language";

    private static final String RFID_MODE_KEY = "rfid.mode";
    private static final String RFID_DEVICE_NAME_KEY = "rfid.device.name";
    private static final String RFID_MODE_KEYBOARD = "KEYBOARD";
    private static final String RFID_MODE_DISABLED = "DISABLED";

    private static final String RECEIPT_STORE_NAME_KEY = "receipt.store.name";
    private static final String RECEIPT_PHONE_KEY = "receipt.store.phone";
    private static final String RECEIPT_TICKET_PREFIX_KEY = "receipt.ticket.prefix";
    private static final String RECEIPT_FOOTER_KEY = "receipt.footer";
    private static final String RECEIPT_CURRENCY_KEY = "receipt.currency.label";
    private static final String RECEIPT_SEPARATOR_KEY = "receipt.separator.char";
    private static final String RECEIPT_SHOW_CUSTOMER_KEY = "receipt.show.customer.block";

    private static final String DEFAULT_RECEIPT_STORE_NAME = "COMMON GROUNDS";
    private static final String DEFAULT_RECEIPT_PHONE = "Tel: 023 484 524";
    private static final String DEFAULT_RECEIPT_TICKET_PREFIX = "TICKET Num";
    private static final String DEFAULT_RECEIPT_FOOTER = "Common Grounds, Uncommon Flavors";
    private static final String DEFAULT_RECEIPT_CURRENCY = "DA";
    private static final String DEFAULT_RECEIPT_SEPARATOR = "*";
    private static final String SUPPORT_PHONE = "+213 771175933";

    private static final int MAX_TOASTS = 3;

    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final PrinterService printerService = new PrinterService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();
    private final UserDAO userDAO = new UserDAO();
    private final BackupService backupService = new BackupService();

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<ActionPolicyRow> actionPolicies = FXCollections.observableArrayList();
    private boolean loadingActionPolicies;

    @FXML
    private ComboBox<String> printerBox;
    @FXML
    private Label printQueueStatusLabel;

    @FXML
    private TextField receiptStoreNameField;
    @FXML
    private TextField receiptPhoneField;
    @FXML
    private TextField receiptTicketPrefixField;
    @FXML
    private TextField receiptFooterField;
    @FXML
    private TextField receiptCurrencyField;
    @FXML
    private TextField receiptSeparatorField;
    @FXML
    private CheckBox receiptShowCustomerCheckBox;

    @FXML
    private ComboBox<String> languageBox;

    @FXML
    private ComboBox<String> rfidModeBox;
    @FXML
    private TextField rfidDeviceNameField;

    @FXML
    private TextField backupDriveField;
    @FXML
    private TextField exportFolderField;

    @FXML
    private TextField stockThresholdField;
    @FXML
    private TextField tvaInput;

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
    private TableView<ActionPolicyRow> actionAccessTable;
    @FXML
    private TableColumn<ActionPolicyRow, String> actionLabelColumn;
    @FXML
    private TableColumn<ActionPolicyRow, UserRole> actionRoleColumn;
    @FXML
    private TableColumn<ActionPolicyRow, Boolean> actionPinColumn;

    @FXML
    private void initialize() {
        configureUsersTable();
        configureLanguageBox();
        configureRfidModeBox();
        configureActionAccessTable();

        newUserRoleBox.getItems().setAll(UserRole.values());
        newUserRoleBox.getSelectionModel().select(UserRole.BARISTA);

        loadPrinters();
        loadReceiptSettings();
        loadLanguageSettings();
        loadRfidSettings();
        loadBackupSettings();
        loadExportSettings();
        loadStockThreshold();
        loadTvaPercent();
        loadUsers();
        loadActionPolicies();
        refreshQueueStatus();
    }

    private void configureLanguageBox() {
        if (languageBox != null) {
            languageBox.getItems().setAll("Francais", "English");
        }
    }

    private void configureRfidModeBox() {
        if (rfidModeBox != null) {
            rfidModeBox.getItems().setAll("Clavier RFID (USB)", "Desactive");
        }
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
                } else if (!names.isEmpty()) {
                    printerBox.getSelectionModel().selectFirst();
                }
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur chargement imprimantes", task.getException()));
        Thread thread = new Thread(task, "printer-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadReceiptSettings() {
        Task<Void> task = new Task<>() {
            private String storeName;
            private String phone;
            private String ticketPrefix;
            private String footer;
            private String currency;
            private String separator;
            private String showCustomer;

            @Override
            protected Void call() throws Exception {
                storeName = readOrDefault(RECEIPT_STORE_NAME_KEY, DEFAULT_RECEIPT_STORE_NAME);
                phone = readOrDefault(RECEIPT_PHONE_KEY, DEFAULT_RECEIPT_PHONE);
                ticketPrefix = readOrDefault(RECEIPT_TICKET_PREFIX_KEY, DEFAULT_RECEIPT_TICKET_PREFIX);
                footer = readOrDefault(RECEIPT_FOOTER_KEY, DEFAULT_RECEIPT_FOOTER);
                currency = readOrDefault(RECEIPT_CURRENCY_KEY, DEFAULT_RECEIPT_CURRENCY);
                separator = readOrDefault(RECEIPT_SEPARATOR_KEY, DEFAULT_RECEIPT_SEPARATOR);
                showCustomer = readOrDefault(RECEIPT_SHOW_CUSTOMER_KEY, "true");
                return null;
            }

            @Override
            protected void succeeded() {
                if (receiptStoreNameField != null) {
                    receiptStoreNameField.setText(storeName);
                }
                if (receiptPhoneField != null) {
                    receiptPhoneField.setText(phone);
                }
                if (receiptTicketPrefixField != null) {
                    receiptTicketPrefixField.setText(ticketPrefix);
                }
                if (receiptFooterField != null) {
                    receiptFooterField.setText(footer);
                }
                if (receiptCurrencyField != null) {
                    receiptCurrencyField.setText(currency);
                }
                if (receiptSeparatorField != null) {
                    receiptSeparatorField.setText(separator);
                }
                if (receiptShowCustomerCheckBox != null) {
                    receiptShowCustomerCheckBox.setSelected(Boolean.parseBoolean(showCustomer));
                }
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur chargement parametres ticket", task.getException()));
        Thread thread = new Thread(task, "receipt-settings-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadLanguageSettings() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String value = settingsDAO.getValue(APP_LANGUAGE_KEY);
                if (value == null || value.isBlank()) {
                    return Locale.getDefault().getLanguage();
                }
                return value.trim();
            }
        };
        task.setOnSucceeded(evt -> {
            if (languageBox == null) {
                return;
            }
            languageBox.getSelectionModel().select(labelFromLanguageCode(task.getValue()));
        });
        task.setOnFailed(evt -> LOG.error("Erreur chargement langue", task.getException()));
        Thread thread = new Thread(task, "language-settings-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadRfidSettings() {
        Task<Void> task = new Task<>() {
            private String mode;
            private String deviceName;

            @Override
            protected Void call() throws Exception {
                mode = readOrDefault(RFID_MODE_KEY, RFID_MODE_KEYBOARD);
                deviceName = readOrDefault(RFID_DEVICE_NAME_KEY, "");
                return null;
            }

            @Override
            protected void succeeded() {
                if (rfidModeBox != null) {
                    rfidModeBox.getSelectionModel().select(labelFromRfidMode(mode));
                }
                if (rfidDeviceNameField != null) {
                    rfidDeviceNameField.setText(deviceName);
                }
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur chargement pairing RFID", task.getException()));
        Thread thread = new Thread(task, "rfid-settings-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadBackupSettings() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String saved = settingsDAO.getValue(BACKUP_TARGET_DIR_KEY);
                if (saved == null || saved.isBlank()) {
                    return backupService.getDefaultBackupDir().toString();
                }
                return saved.trim();
            }
        };
        task.setOnSucceeded(evt -> {
            if (backupDriveField != null) {
                backupDriveField.setText(task.getValue());
            }
        });
        task.setOnFailed(evt -> LOG.error("Erreur chargement dossier backup", task.getException()));
        Thread thread = new Thread(task, "backup-settings-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadExportSettings() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String saved = settingsDAO.getValue(EXPORT_DIR_KEY);
                if (saved == null || saved.isBlank()) {
                    return "";
                }
                return saved.trim();
            }
        };
        task.setOnSucceeded(evt -> {
            if (exportFolderField != null) {
                exportFolderField.setText(task.getValue());
            }
        });
        task.setOnFailed(evt -> LOG.error("Erreur chargement dossier export", task.getException()));
        Thread thread = new Thread(task, "export-settings-load");
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
    private void onSaveReceiptTemplate() {
        String storeName = nonBlankOrDefault(textOrEmpty(receiptStoreNameField), DEFAULT_RECEIPT_STORE_NAME);
        String phone = nonBlankOrDefault(textOrEmpty(receiptPhoneField), DEFAULT_RECEIPT_PHONE);
        String ticketPrefix = nonBlankOrDefault(textOrEmpty(receiptTicketPrefixField), DEFAULT_RECEIPT_TICKET_PREFIX);
        String footer = nonBlankOrDefault(textOrEmpty(receiptFooterField), DEFAULT_RECEIPT_FOOTER);
        String currency = nonBlankOrDefault(textOrEmpty(receiptCurrencyField), DEFAULT_RECEIPT_CURRENCY)
                .toUpperCase(Locale.ROOT);
        String separatorRaw = nonBlankOrDefault(textOrEmpty(receiptSeparatorField), DEFAULT_RECEIPT_SEPARATOR);
        String separator = String.valueOf(separatorRaw.charAt(0));
        boolean showCustomer = receiptShowCustomerCheckBox != null && receiptShowCustomerCheckBox.isSelected();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(RECEIPT_STORE_NAME_KEY, storeName);
                settingsDAO.setValue(RECEIPT_PHONE_KEY, phone);
                settingsDAO.setValue(RECEIPT_TICKET_PREFIX_KEY, ticketPrefix);
                settingsDAO.setValue(RECEIPT_FOOTER_KEY, footer);
                settingsDAO.setValue(RECEIPT_CURRENCY_KEY, currency);
                settingsDAO.setValue(RECEIPT_SEPARATOR_KEY, separator);
                settingsDAO.setValue(RECEIPT_SHOW_CUSTOMER_KEY, String.valueOf(showCustomer));
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Format ticket enregistre"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde ticket", task.getException());
            showToast("error", "Sauvegarde ticket impossible");
        });
        Thread thread = new Thread(task, "receipt-settings-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSaveLanguage() {
        if (languageBox == null || languageBox.getValue() == null) {
            showToast("warning", "Selectionnez une langue");
            return;
        }
        String code = languageCodeFromLabel(languageBox.getValue());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(APP_LANGUAGE_KEY, code);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            MainApp.setAppLocale(MainApp.localeFromCode(code));
            showToast("success", "Langue enregistree. Redemarrez l'application");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde langue", task.getException());
            showToast("error", "Sauvegarde langue impossible");
        });
        Thread thread = new Thread(task, "language-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSaveRfidPairing() {
        if (rfidModeBox == null || rfidModeBox.getValue() == null) {
            showToast("warning", "Selectionnez un mode RFID");
            return;
        }
        String mode = rfidModeFromLabel(rfidModeBox.getValue());
        String deviceName = textOrEmpty(rfidDeviceNameField);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(RFID_MODE_KEY, mode);
                settingsDAO.setValue(RFID_DEVICE_NAME_KEY, deviceName);
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Pairing RFID enregistre"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde RFID", task.getException());
            showToast("error", "Sauvegarde RFID impossible");
        });
        Thread thread = new Thread(task, "rfid-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSelectBackupDrive() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selectionner dossier de sauvegarde");
        Path initial = resolveBackupTargetDir();
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        Window window = currentWindow();
        File selected = chooser.showDialog(window);
        if (selected == null) {
            return;
        }
        if (backupDriveField != null) {
            backupDriveField.setText(selected.getAbsolutePath());
        }
        showToast("info", "Lecteur selectionne");
    }

    @FXML
    private void onSelectExportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selectionner dossier export");
        Path initial = resolveExportDir();
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        Window window = currentWindow();
        File selected = chooser.showDialog(window);
        if (selected == null) {
            return;
        }
        if (exportFolderField != null) {
            exportFolderField.setText(selected.getAbsolutePath());
        }
        showToast("info", "Dossier export selectionne");
    }

    @FXML
    private void onSaveExportFolder() {
        String value = textOrEmpty(exportFolderField);
        if (value.isBlank()) {
            showToast("warning", "Dossier export invalide");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(EXPORT_DIR_KEY, value.trim());
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Dossier export enregistre"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde export", task.getException());
            showToast("error", "Sauvegarde export impossible");
        });
        Thread thread = new Thread(task, "export-settings-save");
        thread.setDaemon(true);
        thread.start();
    }

    private Path resolveExportDir() {
        String value = textOrEmpty(exportFolderField);
        if (value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
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

    private void loadTvaPercent() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String value = settingsDAO.getValue(TVA_PERCENT_KEY);
                if (value == null || value.isBlank()) {
                    return "0";
                }
                return value.trim();
            }
        };
        task.setOnSucceeded(evt -> {
            if (tvaInput != null) {
                tvaInput.setText(task.getValue());
            }
        });
        task.setOnFailed(evt -> LOG.error("Erreur chargement TVA", task.getException()));
        Thread thread = new Thread(task, "tva-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSaveTvaPercent() {
        String raw = textOrEmpty(tvaInput);
        double tva;
        try {
            tva = raw.isBlank() ? 0 : Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            showToast("warning", "TVA invalide");
            return;
        }
        if (tva < 0) {
            showToast("warning", "TVA invalide");
            return;
        }

        String saved = String.valueOf(tva);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(TVA_PERCENT_KEY, saved);
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "TVA enregistree"));
        task.setOnFailed(evt -> {
            LOG.error("Erreur sauvegarde TVA", task.getException());
            showToast("error", "Sauvegarde TVA impossible");
        });
        Thread thread = new Thread(task, "tva-save");
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

    private void configureActionAccessTable() {
        if (actionAccessTable == null) {
            return;
        }
        actionAccessTable.setEditable(true);
        actionAccessTable.setItems(actionPolicies);

        actionLabelColumn.setCellValueFactory(data -> data.getValue().labelProperty());

        actionRoleColumn.setCellValueFactory(data -> data.getValue().roleProperty());
        actionRoleColumn.setCellFactory(ComboBoxTableCell.forTableColumn(UserRole.values()));
        actionRoleColumn.setOnEditCommit(event -> {
            ActionPolicyRow row = event.getRowValue();
            if (row == null) {
                return;
            }
            row.setRole(event.getNewValue());
            persistActionPolicy(row);
        });

        actionPinColumn.setCellValueFactory(data -> data.getValue().pinRequiredProperty());
        actionPinColumn.setCellFactory(CheckBoxTableCell.forTableColumn(actionPinColumn));
    }

    private void loadActionPolicies() {
        if (actionAccessTable == null) {
            return;
        }
        loadingActionPolicies = true;
        actionPolicies.clear();
        for (AppAction action : AppAction.values()) {
            UserRole role = action.getDefaultRole();
            boolean pin = action.isDefaultPinRequired();
            try {
                String roleValue = settingsDAO.getValue("action.role." + action.getKey());
                if (roleValue != null && !roleValue.isBlank()) {
                    role = UserRole.valueOf(roleValue.trim().toUpperCase(Locale.ROOT));
                }
                String pinValue = settingsDAO.getValue("action.pin." + action.getKey());
                if (pinValue != null && !pinValue.isBlank()) {
                    pin = Boolean.parseBoolean(pinValue.trim());
                }
            } catch (Exception ex) {
                LOG.warn("Erreur lecture action {}", action.getKey(), ex);
            }

            ActionPolicyRow row = new ActionPolicyRow(action, role, pin);
            row.pinRequiredProperty().addListener((obs, oldVal, newVal) -> {
                if (!loadingActionPolicies) {
                    persistActionPolicy(row);
                }
            });
            actionPolicies.add(row);
        }
        loadingActionPolicies = false;
    }

    private void persistActionPolicy(ActionPolicyRow row) {
        if (row == null) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue("action.role." + row.getAction().getKey(), row.getRole().name());
                settingsDAO.setValue("action.pin." + row.getAction().getKey(), String.valueOf(row.isPinRequired()));
                return null;
            }
        };
        task.setOnFailed(evt -> LOG.error("Erreur sauvegarde action", task.getException()));
        Thread thread = new Thread(task, "action-policy-save");
        thread.setDaemon(true);
        thread.start();
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
        Path targetDir = resolveBackupTargetDir();
        if (targetDir == null) {
            showToast("warning", "Lecteur de sauvegarde invalide");
            return;
        }

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                settingsDAO.setValue(BACKUP_TARGET_DIR_KEY, targetDir.toString());
                return backupService.runBackup(targetDir);
            }
        };
        task.setOnSucceeded(evt -> {
            Path backupPath = task.getValue();
            backupStatusLabel.setText("Sauvegarde: " + backupPath.toAbsolutePath());
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

    @FXML
    private void onCopySupportNumber() {
        ClipboardContent content = new ClipboardContent();
        content.putString(SUPPORT_PHONE);
        Clipboard.getSystemClipboard().setContent(content);
        showToast("success", "Numero support copie");
    }

    @FXML
    private void onRestoreFromBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Restaurer depuis une sauvegarde");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers DB", "*.db"));
        Path initial = resolveBackupTargetDir();
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        File selected = chooser.showOpenDialog(currentWindow());
        if (selected == null) {
            return;
        }

        Path selectedFile = selected.toPath();
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                Path safeBackupDir = resolveBackupTargetDir();
                if (safeBackupDir != null) {
                    backupService.runBackup(safeBackupDir);
                    settingsDAO.setValue(BACKUP_TARGET_DIR_KEY, safeBackupDir.toString());
                }
                return backupService.stageRestore(selectedFile);
            }
        };
        task.setOnSucceeded(evt -> {
            backupStatusLabel.setText("Restauration preparee. Redemarrez l'application.");
            showToast("warning", "Restauration prete. Redemarrage requis");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur restauration", task.getException());
            showToast("error", "Restauration impossible");
        });
        Thread thread = new Thread(task, "restore-stage");
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
        FontIcon icon = UiIconHelper.statusIcon(type, 18);
        icon.setStyle("-fx-icon-color: " + toastColor(type) + ";");
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

    private String toastColor(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success" -> "-color-success-emphasis";
            case "warning" -> "-color-warning-emphasis";
            case "error", "danger" -> "-color-danger-emphasis";
            default -> "-color-accent-emphasis";
        };
    }

    private String languageCodeFromLabel(String label) {
        if (label == null) {
            return "fr";
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("en") ? "en" : "fr";
    }

    private String labelFromLanguageCode(String code) {
        if (code == null) {
            return "Francais";
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("en") ? "English" : "Francais";
    }

    private String rfidModeFromLabel(String label) {
        if (label == null) {
            return RFID_MODE_KEYBOARD;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("desactive") ? RFID_MODE_DISABLED : RFID_MODE_KEYBOARD;
    }

    private String labelFromRfidMode(String mode) {
        if (mode == null) {
            return "Clavier RFID (USB)";
        }
        return RFID_MODE_DISABLED.equalsIgnoreCase(mode) ? "Desactive" : "Clavier RFID (USB)";
    }

    private String readOrDefault(String key, String fallback) throws Exception {
        String value = settingsDAO.getValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String textOrEmpty(TextField field) {
        if (field == null || field.getText() == null) {
            return "";
        }
        return field.getText().trim();
    }

    private String nonBlankOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private Path resolveBackupTargetDir() {
        try {
            String raw = textOrEmpty(backupDriveField);
            if (raw.isBlank()) {
                return backupService.getDefaultBackupDir();
            }
            return Path.of(raw);
        } catch (Exception ex) {
            LOG.warn("Chemin backup invalide", ex);
            return null;
        }
    }

    private Window currentWindow() {
        if (toastContainer != null && toastContainer.getScene() != null) {
            return toastContainer.getScene().getWindow();
        }
        if (printerBox != null && printerBox.getScene() != null) {
            return printerBox.getScene().getWindow();
        }
        return null;
    }

    public static class ActionPolicyRow {
        private final AppAction action;
        private final SimpleStringProperty label;
        private final javafx.beans.property.ObjectProperty<UserRole> role;
        private final javafx.beans.property.BooleanProperty pinRequired;

        public ActionPolicyRow(AppAction action, UserRole role, boolean pinRequired) {
            this.action = action;
            this.label = new SimpleStringProperty(action.getLabel());
            this.role = new javafx.beans.property.SimpleObjectProperty<>(role);
            this.pinRequired = new javafx.beans.property.SimpleBooleanProperty(pinRequired);
        }

        public AppAction getAction() {
            return action;
        }

        public String getLabel() {
            return label.get();
        }

        public SimpleStringProperty labelProperty() {
            return label;
        }

        public UserRole getRole() {
            return role.get();
        }

        public void setRole(UserRole value) {
            role.set(value);
        }

        public javafx.beans.property.ObjectProperty<UserRole> roleProperty() {
            return role;
        }

        public boolean isPinRequired() {
            return pinRequired.get();
        }

        public javafx.beans.property.BooleanProperty pinRequiredProperty() {
            return pinRequired;
        }
    }
}
