package com.cafepos.ui;

import com.cafepos.MainApp;
import com.cafepos.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DiscountDialog extends Stage {
    public record DiscountSelection(double percent, double amount) {
    }

    private final double subtotal;
    private final TextField discountInput = new TextField();
    private final ToggleButton percentToggle = new ToggleButton("%");
    private final ToggleButton amountToggle = new ToggleButton("DZD");
    private final Label lblAfterDiscount = new Label();

    private DiscountSelection result;

    private DiscountDialog(Stage owner, double subtotalAmount, DiscountSelection current) {
        this.subtotal = Math.max(0, subtotalAmount);

        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        MainApp.applyAppIcon(this);
        setWidth(320);
        setHeight(280);

        StackPane root = new StackPane();
        root.setPadding(new Insets(12));

        VBox panel = new VBox(10);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: -color-bg-inset;"
                + "-fx-border-color: -color-border-default;"
                + "-fx-border-width: 1px;"
                + "-fx-background-radius: 12px;"
                + "-fx-border-radius: 12px;");

        Label title = new Label("Appliquer une remise");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("Remise sur la commande");
        subtitle.setStyle("-fx-font-weight: bold;");

        HBox presets = new HBox(6);
        presets.getChildren().addAll(
                presetButton("10%", 10),
                presetButton("20%", 20),
                presetButton("30%", 30)
        );

        Label inputLabel = new Label("Ou saisir un montant:");
        inputLabel.getStyleClass().add("text-muted");
        inputLabel.setStyle("-fx-font-size: 12px;");

        HBox inputRow = new HBox(6);
        discountInput.setPromptText("0");
        discountInput.setStyle("-fx-font-size: 20px;");
        HBox.setHgrow(discountInput, Priority.ALWAYS);

        ToggleGroup modeGroup = new ToggleGroup();
        percentToggle.setToggleGroup(modeGroup);
        amountToggle.setToggleGroup(modeGroup);
        percentToggle.setPrefSize(52, 44);
        amountToggle.setPrefSize(52, 44);
        inputRow.getChildren().addAll(discountInput, percentToggle, amountToggle);

        HBox totalRow = new HBox(8);
        Label totalText = new Label("Total après remise:");
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        lblAfterDiscount.getStyleClass().add("accent");
        lblAfterDiscount.setStyle("-fx-font-weight: bold;");
        totalRow.getChildren().addAll(totalText, totalSpacer, lblAfterDiscount);

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

        Button apply = new Button("Appliquer");
        apply.getStyleClass().addAll("button", "success");
        apply.setPrefHeight(52);
        apply.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(apply, Priority.ALWAYS);
        apply.setOnAction(evt -> onApply());

        actions.getChildren().addAll(cancel, apply);

        panel.getChildren().addAll(title, subtitle, presets, new Separator(), inputLabel, inputRow, totalRow, actions);
        root.getChildren().add(panel);

        Scene scene = new Scene(root, 320, 280);
        MainApp.applyBrandTheme(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                result = null;
                close();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                onApply();
                event.consume();
            }
        });
        setScene(scene);

        if (current != null && current.percent() > 0) {
            percentToggle.setSelected(true);
            discountInput.setText(String.valueOf(current.percent()));
        } else if (current != null && current.amount() > 0) {
            amountToggle.setSelected(true);
            discountInput.setText(String.valueOf(current.amount()));
        } else {
            percentToggle.setSelected(true);
        }

        discountInput.textProperty().addListener((obs, oldVal, newVal) -> refreshPreview());
        modeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> refreshPreview());

        setOnShown(evt -> {
            centerOnParent(owner);
            discountInput.requestFocus();
            discountInput.positionCaret(discountInput.getText().length());
            refreshPreview();
        });
    }

    public static DiscountSelection showDialog(Stage owner, double subtotal, DiscountSelection current) {
        DiscountDialog dialog = new DiscountDialog(owner, subtotal, current);
        dialog.showAndWait();
        return dialog.result;
    }

    private Button presetButton(String label, double percent) {
        Button button = new Button(label);
        button.getStyleClass().addAll("button", "elevated");
        button.setPrefHeight(52);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(evt -> {
            percentToggle.setSelected(true);
            discountInput.setText(String.valueOf(percent));
            refreshPreview();
        });
        return button;
    }

    private void onApply() {
        double value = parseAmount(discountInput.getText());
        if (value <= 0) {
            result = new DiscountSelection(0, 0);
            close();
            return;
        }

        if (percentToggle.isSelected()) {
            double safePercent = Math.max(0, Math.min(100, value));
            result = new DiscountSelection(safePercent, 0);
        } else {
            double safeAmount = Math.max(0, Math.min(subtotal, value));
            result = new DiscountSelection(0, safeAmount);
        }
        close();
    }

    private void refreshPreview() {
        double value = parseAmount(discountInput.getText());
        double discount;
        if (percentToggle.isSelected()) {
            double safePercent = Math.max(0, Math.min(100, value));
            discount = subtotal * (safePercent / 100.0);
        } else {
            discount = Math.max(0, Math.min(subtotal, value));
        }
        double after = Math.max(0, subtotal - discount);
        lblAfterDiscount.setText(FormatUtils.formatMoney(after));
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
