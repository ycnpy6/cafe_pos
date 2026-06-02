package com.cafepos.ui;

import com.cafepos.MainApp;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class QuickNewClientDialog extends Stage {
    public record QuickClientData(String name, String cardUid) {
    }

    private final TextField nameInput = new TextField();
    private final TextField cardInput = new TextField();
    private final Label messageLabel = new Label();

    private QuickClientData result;

    private QuickNewClientDialog(Stage owner, String suggestedCardUid) {
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        MainApp.applyAppIcon(this);
        setWidth(420);
        setHeight(300);

        StackPane root = new StackPane();
        root.setPadding(new Insets(12));

        VBox panel = new VBox(10);
        panel.setPadding(new Insets(18));
        panel.setStyle("-fx-background-color: -color-bg-inset;"
                + "-fx-border-color: -color-border-default;"
                + "-fx-border-width: 1px;"
                + "-fx-background-radius: 12px;"
                + "-fx-border-radius: 12px;");

        Label title = new Label("Nouveau client rapide");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label nameLabel = new Label("Nom client");
        nameInput.setPromptText("Nom");

        Label cardLabel = new Label("Carte RFID");
        cardInput.setPromptText("Scannez la carte");

        messageLabel.getStyleClass().add("text-muted");
        messageLabel.setStyle("-fx-font-size: 11px;");

        HBox actions = new HBox(8);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().addAll("button", "elevated");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setPrefHeight(48);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        Button create = new Button("Créer");
        create.getStyleClass().addAll("button", "success");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setPrefHeight(48);
        HBox.setHgrow(create, Priority.ALWAYS);
        create.setOnAction(evt -> onCreate());

        actions.getChildren().addAll(cancel, create);

        panel.getChildren().addAll(title, nameLabel, nameInput, cardLabel, cardInput, messageLabel, actions);
        root.getChildren().add(panel);

        Scene scene = new Scene(root, 420, 300);
        MainApp.applyBrandTheme(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                result = null;
                close();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                onCreate();
                event.consume();
            }
        });
        setScene(scene);

        if (suggestedCardUid != null && !suggestedCardUid.isBlank()) {
            cardInput.setText(suggestedCardUid.trim());
            messageLabel.setText("Carte RFID détectée.");
            flashCardField();
        } else {
            messageLabel.setText("Scannez une carte RFID puis validez.");
        }

        setOnShown(evt -> {
            centerOnParent(owner);
            if (nameInput.getText() == null || nameInput.getText().isBlank()) {
                nameInput.requestFocus();
            } else {
                cardInput.requestFocus();
            }
        });
    }

    public static QuickClientData showDialog(Stage owner, String suggestedCardUid) {
        QuickNewClientDialog dialog = new QuickNewClientDialog(owner, suggestedCardUid);
        dialog.showAndWait();
        return dialog.result;
    }

    private void onCreate() {
        String name = safeText(nameInput.getText());
        String cardUid = safeText(cardInput.getText());

        if (name.isBlank()) {
            messageLabel.setText("Nom obligatoire.");
            return;
        }
        if (cardUid.isBlank()) {
            messageLabel.setText("Carte RFID obligatoire.");
            flashCardField();
            cardInput.requestFocus();
            return;
        }

        result = new QuickClientData(name, cardUid);
        close();
    }

    private void flashCardField() {
        cardInput.setStyle("-fx-border-color: #E53935; -fx-border-width: 2px;");
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(180), evt -> cardInput.setStyle("")),
                new KeyFrame(Duration.millis(360), evt -> cardInput.setStyle("-fx-border-color: #E53935; -fx-border-width: 2px;")),
                new KeyFrame(Duration.millis(540), evt -> cardInput.setStyle(""))
        );
        timeline.playFromStart();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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
