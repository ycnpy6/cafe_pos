package com.cafepos.ui;

import com.cafepos.util.FormatUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TopupDialog extends BaseDialog {
    private final String customerName;
    private final double currentBalance;

    private final TextField amountInput = new TextField();
    private final Label afterBalanceLabel = new Label();

    private Double result;

    private TopupDialog(Stage owner, String customerName, double currentBalance) {
        super(owner, 360, 420);
        this.customerName = customerName == null ? "" : customerName;
        this.currentBalance = Math.max(0, currentBalance);
        initializeDialog();
    }

    public static Double showDialog(Stage owner, String customerName, double currentBalance) {
        TopupDialog dialog = new TopupDialog(owner, customerName, currentBalance);
        dialog.showAndWait();
        return dialog.result;
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(12);

        Label title = new Label("Recharge carte");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label customer = new Label(customerName);
        customer.getStyleClass().add("text-bold");

        Label current = new Label("Solde actuel: " + FormatUtils.formatMoney(currentBalance));
        current.getStyleClass().add("text-muted");

        Label amountLabel = new Label("Montant (DZD)");
        amountLabel.getStyleClass().add("text-muted");
        amountInput.setPromptText("0");
        amountInput.setStyle("-fx-font-size: 24px; -fx-alignment: center-right;");
        amountInput.textProperty().addListener((obs, oldVal, newVal) -> refreshAfterBalance());

        HBox quickAmounts = new HBox(8,
                quickButton("200"),
                quickButton("500"),
                quickButton("1000"),
                quickButton("2000")
        );
        quickAmounts.setAlignment(Pos.CENTER);

        refreshAfterBalance();

        HBox afterRow = new HBox(8);
        Label afterText = new Label("Solde après:");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        afterBalanceLabel.setStyle("-fx-font-weight: bold;");
        afterRow.getChildren().addAll(afterText, spacer, afterBalanceLabel);

        HBox actions = new HBox(8);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().addAll("button", "elevated");
        cancel.setPrefHeight(52);
        cancel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        Button confirm = new Button("Valider");
        confirm.getStyleClass().addAll("button", "success");
        confirm.setPrefHeight(52);
        confirm.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        confirm.setOnAction(evt -> confirm());

        actions.getChildren().addAll(cancel, confirm);

        root.getChildren().addAll(
                title,
                customer,
                current,
                new Separator(),
                amountLabel,
                amountInput,
                quickAmounts,
                afterRow,
                new Separator(),
                actions
        );

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        result = null;
                        close();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ENTER) {
                        confirm();
                        event.consume();
                    }
                });
            }
        });

        return root;
    }

    private Button quickButton(String amount) {
        Button button = new Button(amount);
        button.getStyleClass().addAll("button", "elevated");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(evt -> {
            amountInput.setText(amount);
            refreshAfterBalance();
        });
        return button;
    }

    private void refreshAfterBalance() {
        double amount = parseAmount(amountInput.getText());
        afterBalanceLabel.setText(FormatUtils.formatMoney(currentBalance + Math.max(0, amount)));
    }

    private void confirm() {
        double amount = parseAmount(amountInput.getText());
        if (amount <= 0) {
            return;
        }
        result = amount;
        close();
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
}
