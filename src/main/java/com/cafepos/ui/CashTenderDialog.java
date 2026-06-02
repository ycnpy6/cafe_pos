package com.cafepos.ui;

import com.cafepos.MainApp;
import com.cafepos.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class CashTenderDialog extends Stage {
    private final double total;
    private final TextField cashInput = new TextField();
    private final Label lblCashTotal = new Label();
    private final Label lblChange = new Label();

    private Double result;

    private CashTenderDialog(Stage owner, double totalAmount) {
        this.total = totalAmount;

        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        MainApp.applyAppIcon(this);
        setWidth(460);
        setHeight(640);

        StackPane root = new StackPane();
        root.setPadding(new Insets(16));

        VBox panel = new VBox(12);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: -color-bg-inset;"
                + "-fx-border-color: -color-border-default;"
                + "-fx-border-width: 1px;"
                + "-fx-background-radius: 12px;"
                + "-fx-border-radius: 12px;");
        panel.setEffect(new DropShadow(20, Color.rgb(0, 0, 0, 0.4)));

        Label title = new Label("Montant reçu");
        title.getStyleClass().add("text-muted");
        title.setStyle("-fx-font-size: 13px;");

        cashInput.setStyle("-fx-font-size: 28px; -fx-alignment: center-right;");
        cashInput.setPromptText("0");
        cashInput.textProperty().addListener((obs, oldVal, newVal) -> updateChange());

        HBox quickAmounts = new HBox(6);
        quickAmounts.getChildren().addAll(
                quickButton("200"),
                quickButton("500"),
                quickButton("1000"),
                quickButton("2000")
        );

        GridPane numpad = new GridPane();
        numpad.setHgap(6);
        numpad.setVgap(6);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK"};
        for (int i = 0; i < keys.length; i++) {
            int row = i / 3;
            int col = i % 3;
            numpad.add(keyButton(keys[i]), col, row);
        }

        HBox totalRow = new HBox(8);
        Label totalText = new Label("Total:");
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        lblCashTotal.setStyle("-fx-font-weight: bold;");
        totalRow.getChildren().addAll(totalText, totalSpacer, lblCashTotal);

        HBox changeRow = new HBox(8);
        Label changeText = new Label("Rendu:");
        Region changeSpacer = new Region();
        HBox.setHgrow(changeSpacer, Priority.ALWAYS);
        lblChange.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        changeRow.getChildren().addAll(changeText, changeSpacer, lblChange);

        HBox actions = new HBox(8);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().add("button");
        cancel.getStyleClass().add("elevated");
        cancel.setPrefHeight(52);
        cancel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        Button confirm = new Button("Valider");
        confirm.getStyleClass().add("button");
        confirm.getStyleClass().add("success");
        confirm.setPrefHeight(52);
        confirm.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        confirm.setOnAction(evt -> onConfirm());

        actions.getChildren().addAll(cancel, confirm);

        panel.getChildren().addAll(
                title,
                cashInput,
                quickAmounts,
                numpad,
                new Separator(),
                totalRow,
                changeRow,
                actions
        );

        root.getChildren().add(panel);
        Scene scene = new Scene(root, 460, 640);
        MainApp.applyBrandTheme(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                result = null;
                close();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                onConfirm();
                event.consume();
            }
        });
        setScene(scene);

        setOnShown(evt -> {
            centerOnParent(owner);
            cashInput.requestFocus();
            cashInput.positionCaret(cashInput.getText().length());
        });

        lblCashTotal.setText(FormatUtils.formatMoney(total));
        updateChange();
    }

    public static Double showDialog(Stage owner, double totalAmount) {
        CashTenderDialog dialog = new CashTenderDialog(owner, totalAmount);
        dialog.showAndWait();
        return dialog.result;
    }

    public static Double showDialog(Stage owner, double totalAmount, double initialAmount) {
        CashTenderDialog dialog = new CashTenderDialog(owner, totalAmount);
        if (initialAmount > 0) {
            dialog.cashInput.setText(String.valueOf(initialAmount));
            dialog.updateChange();
        }
        dialog.showAndWait();
        return dialog.result;
    }

    private Button quickButton(String amount) {
        Button button = new Button(amount);
        button.setPrefSize(60, 40);
        button.getStyleClass().add("button");
        button.getStyleClass().add("elevated");
        button.setOnAction(evt -> {
            cashInput.setText(amount);
            updateChange();
        });
        return button;
    }

    private Button keyButton(String key) {
        Button button = new Button(key);
        button.setPrefSize(72, 52);
        if ("OK".equalsIgnoreCase(key)) {
            button.getStyleClass().add("button");
            button.getStyleClass().add("success");
            button.setOnAction(evt -> onConfirm());
            return button;
        }

        button.getStyleClass().add("button");
        button.getStyleClass().add("elevated");
        button.setOnAction(evt -> {
            if ("DEL".equalsIgnoreCase(key)) {
                String value = cashInput.getText();
                if (value != null && !value.isEmpty()) {
                    cashInput.setText(value.substring(0, value.length() - 1));
                }
            } else {
                cashInput.setText((cashInput.getText() == null ? "" : cashInput.getText()) + key);
            }
            updateChange();
        });
        return button;
    }

    private void onConfirm() {
        double entered = parseAmount(cashInput.getText());
        if (entered + 0.0001 < total) {
            updateChange();
            return;
        }
        result = entered;
        close();
    }

    private void updateChange() {
        double entered = parseAmount(cashInput.getText());
        double change = entered - total;
        lblChange.setText(FormatUtils.formatMoney(change));
        if (change >= 0) {
            lblChange.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: -color-success-emphasis;");
        } else {
            lblChange.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: -color-danger-emphasis;");
        }
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

    private void centerOnParent(Stage owner) {
        if (owner == null) {
            return;
        }
        double x = owner.getX() + (owner.getWidth() - getWidth()) / 2.0;
        double y = owner.getY() + (owner.getHeight() - getHeight()) / 2.0;
        setX(Math.max(0, x));
        setY(Math.max(0, y));
    }
}
