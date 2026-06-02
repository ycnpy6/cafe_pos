package com.cafepos.ui;

import com.cafepos.util.FormatUtils;
import javafx.concurrent.Task;
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

public class CashWithdrawalDialog extends BaseDialog {
    public record Decision(String reason, double amount) {
    }

    private final double currentCash;
    private final TextField reasonInput = new TextField();
    private final TextField amountInput = new TextField();
    private final Label lblCurrentCash = new Label();
    private final Label errorLabel = new Label();

    private Decision result;

    private CashWithdrawalDialog(Stage owner, double currentCash) {
        super(owner, 360, 340);
        this.currentCash = Math.max(0, currentCash);
        initializeDialog();
    }

    public static Decision showDialog(Stage owner, double currentCash) {
        CashWithdrawalDialog dialog = new CashWithdrawalDialog(owner, currentCash);
        dialog.showAndWait();
        return dialog.result;
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(10);

        Label title = new Label("Retrait d'espèces");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label reasonLabel = new Label("Motif du retrait");
        reasonLabel.getStyleClass().add("text-muted");
        reasonInput.setPromptText("ex: Achat ingrédients, Fournitures...");
        reasonInput.setPrefHeight(48);

        Label amountLabel = new Label("Montant (DZD)");
        amountLabel.getStyleClass().add("text-muted");
        amountInput.setPrefHeight(52);
        amountInput.setStyle("-fx-font-size: 24px; -fx-alignment: center-right;");

        lblCurrentCash.getStyleClass().add("text-muted");
        lblCurrentCash.setStyle("-fx-font-size: 12px;");
        lblCurrentCash.setText("Caisse actuelle: chargement...");

        errorLabel.getStyleClass().add("danger");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

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

        Button confirm = new Button("Enregistrer");
        confirm.getStyleClass().addAll("button", "success");
        confirm.setPrefHeight(52);
        confirm.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        confirm.setOnAction(evt -> confirm());

        actions.getChildren().addAll(cancel, confirm);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(
                title,
                reasonLabel,
                reasonInput,
                amountLabel,
                amountInput,
                lblCurrentCash,
                errorLabel,
                new Separator(),
                spacer,
                actions
        );

        loadCashAsync();

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

    private void loadCashAsync() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return "Caisse actuelle: " + FormatUtils.formatMoney(currentCash);
            }
        };
        task.setOnSucceeded(evt -> lblCurrentCash.setText(task.getValue()));
        Thread thread = new Thread(task, "withdrawal-cash-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void confirm() {
        String reason = reasonInput.getText() == null ? "" : reasonInput.getText().trim();
        double amount = parseAmount(amountInput.getText());
        if (reason.isBlank()) {
            showError("Motif requis");
            return;
        }
        if (amount <= 0) {
            showError("Montant invalide");
            return;
        }
        result = new Decision(reason, amount);
        close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
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
