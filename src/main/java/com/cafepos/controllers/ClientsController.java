package com.cafepos.controllers;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.RFIDDecoder;
import com.cafepos.hardware.RFIDHandler;
import com.cafepos.model.AccountTransaction;
import com.cafepos.model.Customer;
import com.cafepos.model.User;
import com.cafepos.service.AccountService;
import com.cafepos.service.SessionManager;
import com.cafepos.util.CustomerImporter;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.UiIconHelper;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

public class ClientsController {
    private static final Logger LOG = LoggerFactory.getLogger(ClientsController.class);
    private static final int MAX_TOASTS = 3;
    private static final String RFID_MODE_KEY = "rfid.mode";
    private static final String RFID_DEVICE_NAME_KEY = "rfid.device.name";
    private static final String RFID_MODE_DISABLED = "DISABLED";

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AccountTransactionDAO accountTransactionDAO = new AccountTransactionDAO();
    private final AccountService accountService = new AccountService();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final ObservableList<ClientRow> master = FXCollections.observableArrayList();
    private final FilteredList<ClientRow> filtered = new FilteredList<>(master, row -> true);

    private Customer selectedCustomer;

    @FXML
    private StackPane rootStack;
    @FXML
    private TextField searchField;
    @FXML
    private Button newCustomerButton;
    @FXML
    private Button importCsvButton;
    @FXML
    private Button topupToolbarButton;
    @FXML
    private TextField rfidField;

    @FXML
    private TableView<ClientRow> clientsTable;
    @FXML
    private TableColumn<ClientRow, String> nameColumn;
    @FXML
    private TableColumn<ClientRow, String> cardColumn;
    @FXML
    private TableColumn<ClientRow, String> phoneColumn;
    @FXML
    private TableColumn<ClientRow, String> balanceColumn;
    @FXML
    private TableColumn<ClientRow, String> spentColumn;
    @FXML
    private TableColumn<ClientRow, String> lastTxColumn;
    @FXML
    private TableColumn<ClientRow, String> activeColumn;
    @FXML
    private TableColumn<ClientRow, String> actionsColumn;

    @FXML
    private Label statsLabel;

    @FXML
    private VBox topupDialog;
    @FXML
    private Label topupCustomerLabel;
    @FXML
    private Label topupBalanceLabel;
    @FXML
    private TextField topupAmountField;
    @FXML
    private Label topupAfterLabel;

    @FXML
    private VBox toastContainer;

    @FXML
    private void initialize() {
        configureTable();
        configureSearch();
        loadClients();
        setupRfid();

        if (rootStack != null) {
            rootStack.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F7) {
                    searchField.requestFocus();
                    event.consume();
                } else if (event.getCode() == KeyCode.F9) {
                    onOpenTopup();
                    event.consume();
                } else if (event.getCode() == KeyCode.N && event.isControlDown()) {
                    onNewCustomer();
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    onTopupCancel();
                    event.consume();
                } else if (event.getCode() == KeyCode.ENTER) {
                    if (topupDialog.isVisible()) {
                        onTopupConfirm();
                        event.consume();
                    } else if (searchField.isFocused()) {
                        onSearchEnter();
                        event.consume();
                    }
                }
            });
        }

        topupAmountField.textProperty().addListener((obs, oldVal, newVal) -> updateTopupAfter());
        clientsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedCustomer = newVal == null ? null : newVal.customer();
        });
    }

    private void configureTable() {
        clientsTable.setItems(filtered);
        clientsTable.setPlaceholder(new Label("Aucun client. Cliquez sur 'Nouveau' ou 'Importer CSV'."));

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().customer().getName()));
        cardColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().customer().getCardUid() == null ? "" : data.getValue().customer().getCardUid()));
        if (phoneColumn != null) {
            phoneColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().phone()));
        }
        balanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().customer().getBalance())));
        if (spentColumn != null) {
            spentColumn.setCellValueFactory(data -> new SimpleStringProperty(
                    FormatUtils.formatMoney(data.getValue().lifetimeSpent())));
        }
        lastTxColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastTransaction()));
        if (activeColumn != null) {
            activeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().customer().isActive() ? "Actif" : "Inactif"));
        }

        balanceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(value);
                ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                if (row == null) {
                    return;
                }
                double balance = row.customer().getBalance();
                String color;
                if (balance < 100) {
                    color = "-color-danger-emphasis";
                } else if (balance <= 500) {
                    color = "-color-warning-emphasis";
                } else {
                    color = "-color-success-emphasis";
                }
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 700;");
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button topupButton = new Button("+ Crédit");
            private final HBox box = new HBox(6, topupButton);

            {
                topupButton.getStyleClass().add("ghost-button");
                topupButton.setOnAction(event -> {
                    ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                    if (row != null) {
                        clientsTable.getSelectionModel().select(row);
                        openTopup(row.customer());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Double-click on a row -> open edit dialog
        clientsTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<ClientRow> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openEditDialog(row.getItem().customer());
                }
            });
            return row;
        });
    }

    private void configureSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase();
            filtered.setPredicate(row -> {
                if (query.isBlank()) {
                    return true;
                }
                return row.customer().getName().toLowerCase().contains(query)
                        || (row.customer().getCardUid() != null && row.customer().getCardUid().toLowerCase().contains(query))
                        || row.phone().toLowerCase().contains(query);
            });
        });
    }

    private void loadClients() {
        Task<List<Customer>> task = new Task<>() {
            private Map<Integer, String> lastDates;
            private Map<Integer, CustomerDAO.CustomerExtras> extras;

            @Override
            protected List<Customer> call() throws Exception {
                List<Customer> customers = customerDAO.findAll();
                lastDates = accountTransactionDAO.findLastTransactionDates();
                extras = customerDAO.loadAllExtras();
                return customers;
            }

            @Override
            protected void succeeded() {
                master.clear();
                double totalBalance = 0;
                double totalSpent = 0;
                for (Customer customer : getValue()) {
                    String last = lastDates.getOrDefault(customer.getId(), "");
                    CustomerDAO.CustomerExtras ex = extras.getOrDefault(customer.getId(), CustomerDAO.CustomerExtras.EMPTY);
                    master.add(new ClientRow(customer, FormatUtils.formatDateTime(last), ex.phone(), ex.lifetimeSpent()));
                    totalBalance += customer.getBalance();
                    totalSpent += ex.lifetimeSpent();
                }
                if (statsLabel != null) {
                    statsLabel.setText(getValue().size() + " clients \u2022 Solde total: " +
                            FormatUtils.formatMoney(totalBalance) +
                            " \u2022 Total depense: " + FormatUtils.formatMoney(totalSpent));
                }
            }
        };
        task.setOnFailed(evt -> {
            LOG.error("Erreur clients", task.getException());
            showToast("error", "Clients indisponibles");
        });
        Thread thread = new Thread(task, "clients-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadTransactions() {
        // history is now shown in the edit dialog only
    }

    @FXML
    private void onNewCustomer() {
        openEditDialog(null);
    }

    @FXML
    private void onImportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer des clients depuis un CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv"));
        Window owner = rootStack.getScene() == null ? null : rootStack.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        if (importCsvButton != null) importCsvButton.setDisable(true);
        Task<CustomerImporter.ImportResult> task = new Task<>() {
            @Override
            protected CustomerImporter.ImportResult call() throws Exception {
                return CustomerImporter.importFromFile(file.toPath());
            }
        };
        task.setOnSucceeded(evt -> {
            if (importCsvButton != null) importCsvButton.setDisable(false);
            CustomerImporter.ImportResult r = task.getValue();
            String kind = r.failed() > 0 ? "warning" : "success";
            showToast(kind, "Import: " + r.summary());
            if (r.hasErrors()) {
                LOG.warn("Erreurs import: {}", r.errors());
            }
            loadClients();
        });
        task.setOnFailed(evt -> {
            if (importCsvButton != null) importCsvButton.setDisable(false);
            LOG.error("Erreur import CSV", task.getException());
            showToast("error", "Import CSV impossible");
        });
        Thread t = new Thread(task, "csv-import");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onAddCustomer() {
        // Legacy entry point (no longer wired in FXML). Kept for keyboard shortcut compatibility.
        openEditDialog(null);
    }

    /**
     * Opens the modal edit dialog. Pass {@code null} to create a new customer,
     * or an existing {@link Customer} to edit it.
     */
    private void openEditDialog(Customer customer) {
        boolean isNew = (customer == null);
        final int customerId = isNew ? -1 : customer.getId();

        // Load extras + recent transactions in the background, then build dialog
        Task<Object[]> loader = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                CustomerDAO.CustomerExtras ex = isNew
                        ? CustomerDAO.CustomerExtras.EMPTY
                        : customerDAO.loadExtras(customerId);
                List<AccountTransaction> history = isNew
                        ? java.util.Collections.emptyList()
                        : accountTransactionDAO.findRecentByCustomer(customerId, 50);
                return new Object[]{ ex, history };
            }
        };
        loader.setOnSucceeded(evt -> {
            Object[] data = loader.getValue();
            CustomerDAO.CustomerExtras ex = (CustomerDAO.CustomerExtras) data[0];
            @SuppressWarnings("unchecked")
            List<AccountTransaction> history = (List<AccountTransaction>) data[1];
            showEditDialog(customer, ex, history);
        });
        loader.setOnFailed(evt -> {
            LOG.error("Chargement fiche client", loader.getException());
            showToast("error", "Fiche client indisponible");
        });
        Thread t = new Thread(loader, "client-detail-load");
        t.setDaemon(true);
        t.start();
    }

    private void showEditDialog(Customer customer, CustomerDAO.CustomerExtras extras,
                                List<AccountTransaction> history) {
        boolean isNew = (customer == null);

        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(isNew ? "Nouveau client" : "Fiche client");
        dialog.setHeaderText(isNew ? "Creer un client" : customer.getName());
        dialog.initOwner(rootStack.getScene() == null ? null : rootStack.getScene().getWindow());

        javafx.scene.control.ButtonType saveType = new javafx.scene.control.ButtonType(
                isNew ? "Creer" : "Enregistrer", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType deleteType = new javafx.scene.control.ButtonType(
                "Supprimer", javafx.scene.control.ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(saveType);
        if (!isNew) {
            dialog.getDialogPane().getButtonTypes().add(deleteType);
        }
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);

        // Form fields
        TextField nameField = new TextField(isNew ? "" : customer.getName());
        nameField.setPromptText("Nom complet");
        TextField cardField = new TextField(isNew ? "" : (customer.getCardUid() == null ? "" : customer.getCardUid()));
        cardField.setPromptText("UID carte RFID (optionnel)");
        TextField phoneField = new TextField(extras.phone());
        phoneField.setPromptText("Telephone");
        TextField emailField = new TextField(extras.email());
        emailField.setPromptText("Email");
        TextField addressField = new TextField(extras.address());
        addressField.setPromptText("Adresse");
        TextField balanceField = new TextField(isNew ? "0" : String.valueOf(customer.getBalance()));
        balanceField.setPromptText(isNew ? "Solde initial" : "Solde courant");
        javafx.scene.control.CheckBox activeBox = new javafx.scene.control.CheckBox("Compte actif");
        activeBox.setSelected(isNew || customer.isActive());

        Label spentLabel = new Label(FormatUtils.formatMoney(extras.lifetimeSpent()));
        Label visitsLabel = new Label(String.valueOf(extras.visitCount()));
        Label lastVisitLabel = new Label(extras.lastVisitAt().isBlank() ? "-" : extras.lastVisitAt());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new javafx.geometry.Insets(10));
        int r = 0;
        grid.add(new Label("Nom *"),       0, r); grid.add(nameField,    1, r++);
        grid.add(new Label("Carte RFID"),  0, r); grid.add(cardField,    1, r++);
        grid.add(new Label("Telephone"),   0, r); grid.add(phoneField,   1, r++);
        grid.add(new Label("Email"),       0, r); grid.add(emailField,   1, r++);
        grid.add(new Label("Adresse"),     0, r); grid.add(addressField, 1, r++);
        grid.add(new Label("Solde (DZD)"), 0, r); grid.add(balanceField, 1, r++);
        grid.add(new Label("Etat"),        0, r); grid.add(activeBox,    1, r++);
        if (!isNew) {
            grid.add(new javafx.scene.control.Separator(), 0, r, 2, 1); r++;
            grid.add(new Label("Total depense"), 0, r); grid.add(spentLabel,     1, r++);
            grid.add(new Label("Nombre passages"), 0, r); grid.add(visitsLabel,  1, r++);
            grid.add(new Label("Derniere visite"), 0, r); grid.add(lastVisitLabel, 1, r++);
        }
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setMinWidth(120);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        c2.setMinWidth(260);
        grid.getColumnConstraints().addAll(c1, c2);

        VBox content = new VBox(10, grid);
        if (!isNew && history != null) {
            Label histTitle = new Label("Historique (50 dernieres)");
            histTitle.getStyleClass().add("section-title");
            TableView<AccountTransaction> historyTable = buildHistoryTable(history);
            historyTable.setPrefHeight(180);
            historyTable.setMaxHeight(220);
            content.getChildren().addAll(new javafx.scene.control.Separator(), histTitle, historyTable);
        }
        content.setPrefWidth(540);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(560);

        // Cap dialog size to ~80% of owner window so it always fits the screen
        double maxH = 600;
        double maxW = 620;
        javafx.stage.Window owner = rootStack.getScene() == null ? null : rootStack.getScene().getWindow();
        if (owner != null) {
            maxH = Math.max(360, owner.getHeight() * 0.85);
            maxW = Math.max(420, Math.min(720, owner.getWidth() * 0.7));
        }
        scroll.setPrefViewportHeight(maxH - 120);
        scroll.setMaxHeight(maxH - 120);

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(maxW);
        dialog.getDialogPane().setMaxWidth(maxW);
        dialog.getDialogPane().setMaxHeight(maxH);
        dialog.setResizable(true);

        java.util.Optional<javafx.scene.control.ButtonType> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        javafx.scene.control.ButtonType chosen = result.get();
        if (chosen == javafx.scene.control.ButtonType.CANCEL) return;

        if (chosen == deleteType && !isNew) {
            deleteCustomer(customer);
            return;
        }

        // Save / create
        String name = safeString(nameField.getText());
        String cardUid = RFIDDecoder.normalize(cardField.getText());
        String phone = safeString(phoneField.getText());
        String email = safeString(emailField.getText());
        String address = safeString(addressField.getText());
        double balance = parseAmount(balanceField.getText());
        boolean active = activeBox.isSelected();

        if (name.isBlank()) {
            showToast("warning", "Nom requis");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    int id;
                    if (isNew) {
                        id = customerDAO.insertCustomer(conn, name, cardUid.isBlank() ? null : cardUid, balance, active);
                        if (balance > 0) {
                            Integer userId = currentUserId();
                            accountTransactionDAO.insertTransaction(conn, id, balance, "Solde initial",
                                    userId == null ? 0 : userId, balance, null);
                        }
                    } else {
                        id = customer.getId();
                        customerDAO.updateName(conn, id, name);
                        customerDAO.updateCardUid(conn, id, cardUid);
                        customerDAO.updateActive(conn, id, active);
                        if (Math.abs(balance - customer.getBalance()) > 0.001) {
                            customerDAO.updateBalance(conn, id, balance);
                            Integer userId = currentUserId();
                            double delta = balance - customer.getBalance();
                            accountTransactionDAO.insertTransaction(conn, id, delta,
                                    "Ajustement manuel", userId == null ? 0 : userId, balance, null);
                        }
                    }
                    customerDAO.updateExtraFields(conn, id, phone, email, address, null, null, null);
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", isNew ? "Client cree" : "Fiche enregistree");
            loadClients();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur enregistrement client", task.getException());
            showToast("error", "Enregistrement impossible");
        });
        Thread th = new Thread(task, "client-save");
        th.setDaemon(true);
        th.start();
    }

    private TableView<AccountTransaction> buildHistoryTable(List<AccountTransaction> rows) {
        TableView<AccountTransaction> table = new TableView<>();
        TableColumn<AccountTransaction, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(FormatUtils.formatDateTime(d.getValue().createdAt())));
        dateCol.setPrefWidth(140);
        TableColumn<AccountTransaction, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description()));
        descCol.setPrefWidth(180);
        TableColumn<AccountTransaction, String> amtCol = new TableColumn<>("Montant");
        amtCol.setCellValueFactory(d -> new SimpleStringProperty(FormatUtils.formatMoney(d.getValue().amount())));
        amtCol.setPrefWidth(100);
        TableColumn<AccountTransaction, String> balCol = new TableColumn<>("Solde");
        balCol.setCellValueFactory(d -> new SimpleStringProperty(FormatUtils.formatMoney(d.getValue().balanceAfter())));
        balCol.setPrefWidth(100);
        table.getColumns().addAll(java.util.List.of(dateCol, descCol, amtCol, balCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getItems().setAll(rows);
        return table;
    }

    private void editCustomer(Customer customer) {
        openEditDialog(customer);
    }

    private void deleteCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer client");
        alert.setHeaderText("Supprimer " + customer.getName() + " ?");
        alert.setContentText("Cette action peut échouer si le client est lié à des transactions.");
        if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
            != javafx.scene.control.ButtonType.OK) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    customerDAO.deleteCustomer(conn, customer.getId());
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadClients());
        task.setOnFailed(evt -> {
            LOG.error("Erreur suppression client", task.getException());
            showToast("error", "Suppression client impossible");
        });
        Thread thread = new Thread(task, "client-delete");
        thread.setDaemon(true);
        thread.start();
    }

    private void toggleCustomerActive(Customer customer) {
        if (customer == null) {
            return;
        }
        boolean newState = !customer.isActive();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    customerDAO.updateActive(conn, customer.getId(), newState);
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadClients());
        task.setOnFailed(evt -> {
            LOG.error("Erreur activation client", task.getException());
            showToast("error", "Changement etat client impossible");
        });
        Thread thread = new Thread(task, "client-toggle-active");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onOpenTopup() {
        ClientRow row = clientsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            showToast("warning", "Selectionnez un client");
            return;
        }
        openTopup(row.customer());
    }

    private void openTopup(Customer customer) {
        if (customer == null || !customer.isActive()) {
            showToast("warning", "Client inactif");
            return;
        }
        selectedCustomer = customer;
        topupCustomerLabel.setText(customer.getName());
        topupBalanceLabel.setText("Solde actuel: " + FormatUtils.formatMoney(customer.getBalance()));
        topupAmountField.setText("");
        updateTopupAfter();
        topupDialog.setVisible(true);
        topupDialog.setManaged(true);
        Platform.runLater(topupAmountField::requestFocus);
    }

    @FXML
    private void onTopupQuick(javafx.event.ActionEvent event) {
        Object source = event.getSource();
        if (source instanceof Button button && button.getUserData() != null) {
            topupAmountField.setText(String.valueOf(button.getUserData()));
        }
    }

    @FXML
    private void onTopupCancel() {
        topupDialog.setVisible(false);
        topupDialog.setManaged(false);
    }

    @FXML
    private void onTopupConfirm() {
        if (selectedCustomer == null) {
            return;
        }
        double amount = parseAmount(topupAmountField.getText());
        if (amount < 100) {
            showToast("warning", "Minimum 100 DZD");
            return;
        }
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return accountService.topUp(selectedCustomer, amount);
            }
        };
        task.setOnSucceeded(evt -> {
            topupDialog.setVisible(false);
            topupDialog.setManaged(false);
            loadClients();
            if (selectedCustomer != null) {
                selectedCustomer = task.getValue();
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur recharge", task.getException());
            showToast("error", "Recharge impossible");
        });
        Thread thread = new Thread(task, "topup");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onOpenReports() {
        showToast("info", "Disponible dans Rapports");
    }

    private void onSearchEnter() {
        lookupCard(searchField.getText());
    }

    private void setupRfid() {
        if (rfidField == null) {
            return;
        }

        String rfidMode = null;
        String rfidDeviceName = null;
        try {
            rfidMode = settingsDAO.getValue(RFID_MODE_KEY);
            rfidDeviceName = settingsDAO.getValue(RFID_DEVICE_NAME_KEY);
        } catch (Exception ex) {
            LOG.warn("Lecture parametres RFID impossible", ex);
        }

        if (RFID_MODE_DISABLED.equalsIgnoreCase(rfidMode)) {
            rfidField.setDisable(true);
            rfidField.setPromptText("RFID desactive");
            return;
        }

        rfidField.setDisable(false);
        if (rfidDeviceName != null && !rfidDeviceName.isBlank()) {
            rfidField.setPromptText("RFID: " + rfidDeviceName.trim());
        }

        RFIDHandler handler = new RFIDHandler(rfidField);
        handler.setOnCard(this::lookupCard);
        Platform.runLater(rfidField::requestFocus);
    }

    private void lookupCard(String raw) {
        String value = RFIDDecoder.normalize(raw);
        if (value.length() < 6 || value.length() > 20) {
            return;
        }
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return customerDAO.findActiveByCardUid(value);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer customer = task.getValue();
            if (customer == null) {
                showToast("warning", "Carte inconnue");
                return;
            }
            openTopup(customer);
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur recherche carte", task.getException());
            showToast("error", "Recherche carte impossible");
        });
        Thread thread = new Thread(task, "card-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateTopupAfter() {
        if (selectedCustomer == null) {
            topupAfterLabel.setText("");
            return;
        }
        double amount = parseAmount(topupAmountField.getText());
        double after = selectedCustomer.getBalance() + amount;
        topupAfterLabel.setText("Solde après: " + FormatUtils.formatMoney(after));
    }

    private Integer currentUserId() {
        User user = SessionManager.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
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

    public record ClientRow(Customer customer, String lastTransaction, String phone, double lifetimeSpent) {
    }
}
