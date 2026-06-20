package com.cafepos.controllers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cafepos.dao.CustomerDAO;
import com.cafepos.hardware.RFIDDecoder;
import com.cafepos.model.Customer;
import com.cafepos.service.AccountService;
import com.cafepos.util.FormatUtils;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class CustomersController {
    private static final Logger LOG = LoggerFactory.getLogger(CustomersController.class);
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final AccountService accountService = new AccountService();

    @FXML
    private TableView<Customer> customersTable;
    @FXML
    private TableColumn<Customer, String> nameColumn;
    @FXML
    private TableColumn<Customer, String> cardColumn;
    @FXML
    private TableColumn<Customer, String> balanceColumn;
    @FXML
    private TextField amountField;
    @FXML
    private TextField customerNameField;
    @FXML
    private TextField cardUidField;
    @FXML
    private TextField initialBalanceField;

    @FXML
    private void initialize() {
        configureTable();
        loadCustomers();
    }

    private void configureTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        cardColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCardUid()));
        balanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().getBalance())));
    }

    private void loadCustomers() {
        Task<List<Customer>> task = new Task<>() {
            @Override
            protected List<Customer> call() throws Exception {
                return customerDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> customersTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> LOG.error("Erreur clients", task.getException()));
        Thread thread = new Thread(task, "customers-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onTopUp() {
        Customer customer = customersTable.getSelectionModel().getSelectedItem();
        if (customer == null) {
            showAlert("Client requis", "Selectionnez un client.");
            return;
        }
        double amount = parseAmount(amountField.getText());
        if (amount < 100) {
            showAlert("Montant invalide", "Minimum 100 DZD.");
            return;
        }

        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return accountService.topUp(customer, amount);
            }
        };
        task.setOnSucceeded(evt -> {
            amountField.clear();
            loadCustomers();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur recharge", task.getException());
            showAlert("Erreur", "Recharge impossible.");
        });
        Thread thread = new Thread(task, "topup");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onAddCustomer() {
        String name = customerNameField.getText();
        String cardUid = RFIDDecoder.normalize(cardUidField.getText());
        double balance = parseAmount(initialBalanceField.getText());
        if (name == null || name.isBlank()) {
            showAlert("Nom requis", "Saisissez un nom.");
            return;
        }
        if (cardUid.isBlank()) {
            showAlert("Carte requise", "Saisissez l'UID carte.");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                customerDAO.insertCustomer(name.trim(), cardUid, balance);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            customerNameField.clear();
            cardUidField.clear();
            initialBalanceField.clear();
            loadCustomers();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout client", task.getException());
            showAlert("Erreur", "Ajout client impossible.");
        });
        Thread thread = new Thread(task, "customer-add");
        thread.setDaemon(true);
        thread.start();
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
