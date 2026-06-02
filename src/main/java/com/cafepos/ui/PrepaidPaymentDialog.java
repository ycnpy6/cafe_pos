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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PrepaidPaymentDialog extends BaseDialog {
    public enum Action {
        PAY_PREPAID,
        MIXED_CASH,
        TOPUP_CARD,
        CANCEL
    }

    public record Decision(String cardUid, Action action) {
    }

    private final double totalAmount;
    private final String suggestedCardUid;

    private final TextField cardInput = new TextField();
    private final Label infoLabel = new Label();

    private Decision result;

    private PrepaidPaymentDialog(Stage owner, double totalAmount, String suggestedCardUid) {
        super(owner, 500, 360);
        this.totalAmount = Math.max(0, totalAmount);
        this.suggestedCardUid = suggestedCardUid == null ? "" : suggestedCardUid.trim();
        initializeDialog();
    }

    public static Decision showDialog(Stage owner, double totalAmount, String suggestedCardUid) {
        PrepaidPaymentDialog dialog = new PrepaidPaymentDialog(owner, totalAmount, suggestedCardUid);
        dialog.showAndWait();
        return dialog.result;
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(10);

        Label title = new Label("Paiement prépayé");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label totalLabel = new Label("Total: " + FormatUtils.formatMoney(totalAmount));
        totalLabel.getStyleClass().add("text-bold");

        Label cardLabel = new Label("Carte RFID");
        cardLabel.getStyleClass().add("text-muted");

        cardInput.setPromptText("Scannez ou saisissez le code carte");
        if (!suggestedCardUid.isBlank()) {
            cardInput.setText(suggestedCardUid);
        }

        infoLabel.setText("Choisissez: prépayé total, mixte espèces, ou recharge.");
        infoLabel.getStyleClass().add("text-muted");

        HBox actions = new HBox(8);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().addAll("button", "elevated");
        cancel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> {
            result = new Decision(safeCardUid(), Action.CANCEL);
            close();
        });

        Button topup = new Button("Recharger carte");
        topup.getStyleClass().addAll("button", "warning");
        topup.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(topup, Priority.ALWAYS);
        topup.setOnAction(evt -> {
            result = new Decision(safeCardUid(), Action.TOPUP_CARD);
            close();
        });

        Button mixed = new Button("Mixte + espèces");
        mixed.getStyleClass().addAll("button", "accent");
        mixed.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(mixed, Priority.ALWAYS);
        mixed.setOnAction(evt -> {
            result = new Decision(safeCardUid(), Action.MIXED_CASH);
            close();
        });

        Button pay = new Button("Valider prépayé");
        pay.getStyleClass().addAll("button", "success");
        pay.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pay, Priority.ALWAYS);
        pay.setOnAction(evt -> {
            result = new Decision(safeCardUid(), Action.PAY_PREPAID);
            close();
        });

        actions.getChildren().addAll(cancel, topup, mixed, pay);
        actions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(
                title,
                totalLabel,
                new Separator(),
                cardLabel,
                cardInput,
                infoLabel,
                new Separator(),
                actions
        );

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        result = new Decision(safeCardUid(), Action.CANCEL);
                        close();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ENTER) {
                        result = new Decision(safeCardUid(), Action.PAY_PREPAID);
                        close();
                        event.consume();
                    }
                });
            }
        });

        return root;
    }

    private String safeCardUid() {
        String value = cardInput.getText();
        return value == null ? "" : value.trim().toUpperCase();
    }
}
