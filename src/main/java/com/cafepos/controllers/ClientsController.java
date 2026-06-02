package com.cafepos.controllers;

import com.cafepos.dao.AccountTransactionDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.hardware.RFIDHandler;
import com.cafepos.model.AccountTransaction;
import com.cafepos.model.Customer;
import com.cafepos.model.User;
import com.cafepos.service.AccountService;
import com.cafepos.service.SessionManager;
import com.cafepos.util.FormatUtils;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

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
    private TableColumn<ClientRow, String> balanceColumn;
    @FXML
    private TableColumn<ClientRow, String> lastTxColumn;
    @FXML
    private TableColumn<ClientRow, String> activeColumn;
    @FXML
    private TableColumn<ClientRow, String> actionsColumn;

    @FXML
    private VBox detailPane;
    @FXML
    private TableView<AccountTransaction> transactionsTable;
    @FXML
    private TableColumn<AccountTransaction, String> txDateColumn;
    @FXML
    private TableColumn<AccountTransaction, String> txTypeColumn;
    @FXML
    private TableColumn<AccountTransaction, String> txAmountColumn;
    @FXML
    private TableColumn<AccountTransaction, String> txBalanceColumn;
    @FXML
    private TableColumn<AccountTransaction, String> txOrderColumn;

    @FXML
    private VBox newCustomerPane;
    @FXML
    private TextField newNameField;
    @FXML
    private TextField newCardField;
    @FXML
    private TextField newBalanceField;

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
        configureTransactionsTable();
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
                    onToggleNewCustomer();
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
            loadTransactions();
        });
    }

    private void configureTable() {
        clientsTable.setItems(filtered);
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().customer().getName()));
        cardColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().customer().getCardUid()));
        balanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().customer().getBalance())));
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
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Suppr");
            private final Button toggleButton = new Button();
            private final Button topupButton = new Button("+ Crédit");
            private final HBox box = new HBox(6, editButton, deleteButton, toggleButton, topupButton);

            {
                editButton.getStyleClass().add("ghost-button");
                deleteButton.getStyleClass().add("ghost-button");
                toggleButton.getStyleClass().add("ghost-button");
                topupButton.getStyleClass().add("ghost-button");

                editButton.setOnAction(event -> {
                    ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                    if (row != null) {
                        clientsTable.getSelectionModel().select(row);
                        editCustomer(row.customer());
                    }
                });

                deleteButton.setOnAction(event -> {
                    ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                    if (row != null) {
                        clientsTable.getSelectionModel().select(row);
                        deleteCustomer(row.customer());
                    }
                });

                toggleButton.setOnAction(event -> {
                    ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                    if (row != null) {
                        clientsTable.getSelectionModel().select(row);
                        toggleCustomerActive(row.customer());
                    }
                });

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
                if (empty) {
                    setGraphic(null);
                } else {
                    ClientRow row = getTableRow() == null ? null : (ClientRow) getTableRow().getItem();
                    if (row != null) {
                        toggleButton.setText(row.customer().isActive() ? "Desactiver" : "Activer");
                    }
                    setGraphic(box);
                }
            }
        });
    }

    private void configureTransactionsTable() {
        txDateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatDateTime(data.getValue().createdAt())));
        txTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        txAmountColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().amount())));
        txBalanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().balanceAfter())));
        txOrderColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().orderId() == null ? "" : String.valueOf(data.getValue().orderId())));
    }

    private void configureSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase();
            filtered.setPredicate(row -> {
                if (query.isBlank()) {
                    return true;
                }
                return row.customer().getName().toLowerCase().contains(query)
                        || row.customer().getCardUid().toLowerCase().contains(query);
            });
        });
    }

    private void loadClients() {
        Task<List<Customer>> task = new Task<>() {
            private Map<Integer, String> lastDates;

            @Override
            protected List<Customer> call() throws Exception {
                List<Customer> customers = customerDAO.findAll();
                lastDates = accountTransactionDAO.findLastTransactionDates();
                return customers;
            }

            @Override
            protected void succeeded() {
                master.clear();
                for (Customer customer : getValue()) {
                    String last = lastDates.getOrDefault(customer.getId(), "");
                    master.add(new ClientRow(customer, FormatUtils.formatDateTime(last)));
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
        if (selectedCustomer == null) {
            detailPane.setVisible(false);
            detailPane.setManaged(false);
            return;
        }
        detailPane.setVisible(true);
        detailPane.setManaged(true);
        Task<List<AccountTransaction>> task = new Task<>() {
            @Override
            protected List<AccountTransaction> call() throws Exception {
                return accountTransactionDAO.findRecentByCustomer(selectedCustomer.getId(), 20);
            }
        };
        task.setOnSucceeded(evt -> transactionsTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur transactions", task.getException());
            showToast("error", "Historique indisponible");
        });
        Thread thread = new Thread(task, "transactions-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onToggleNewCustomer() {
        boolean show = !newCustomerPane.isVisible();
        newCustomerPane.setVisible(show);
        newCustomerPane.setManaged(show);
        if (show) {
            Platform.runLater(newNameField::requestFocus);
        }
    }

    @FXML
    private void onAddCustomer() {
        String name = safeString(newNameField.getText());
        String uid = safeString(newCardField.getText());
        double balance = parseAmount(newBalanceField.getText());
        if (name.isBlank() || uid.isBlank()) {
            showToast("warning", "Nom et UID requis");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    int id = customerDAO.insertCustomer(conn, name, uid, balance);
                    if (balance > 0) {
                        Integer userId = currentUserId();
                        accountTransactionDAO.insertTransaction(conn, id, balance, "Solde initial", userId == null ? 0 : userId,
                                balance, null);
                    }
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            newNameField.clear();
            newCardField.clear();
            newBalanceField.clear();
            newCustomerPane.setVisible(false);
            newCustomerPane.setManaged(false);
            loadClients();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout client", task.getException());
            showToast("error", "Ajout client impossible");
        });
        Thread thread = new Thread(task, "client-add");
        thread.setDaemon(true);
        thread.start();
    }

    private void editCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        TextInputDialog nameDialog = new TextInputDialog(customer.getName());
        nameDialog.setTitle("Editer client");
        nameDialog.setHeaderText("Nom client");
        nameDialog.setContentText("Nom:");
        if (nameDialog.showAndWait().isEmpty()) {
            return;
        }
        String name = safeString(nameDialog.getResult());
        if (name.isBlank()) {
            showToast("warning", "Nom requis");
            return;
        }

        TextInputDialog cardDialog = new TextInputDialog(customer.getCardUid());
        cardDialog.setTitle("Editer client");
        cardDialog.setHeaderText("UID carte");
        cardDialog.setContentText("UID:");
        if (cardDialog.showAndWait().isEmpty()) {
            return;
        }
        String cardUid = safeString(cardDialog.getResult());
        if (cardUid.isBlank()) {
            showToast("warning", "UID carte requis");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    customerDAO.updateName(conn, customer.getId(), name);
                    customerDAO.updateCardUid(conn, customer.getId(), cardUid);
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadClients());
        task.setOnFailed(evt -> {
            LOG.error("Erreur edition client", task.getException());
            showToast("error", "Edition client impossible");
        });
        Thread thread = new Thread(task, "client-edit");
        thread.setDaemon(true);
        thread.start();
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
        String value = safeString(raw).toUpperCase();
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
            case "warning" -> "!";
            case "error" -> "X";
            default -> "i";
        };
    }

    public record ClientRow(Customer customer, String lastTransaction) {
    }
}
