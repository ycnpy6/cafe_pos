package com.cafepos.ui;

import com.cafepos.util.UiIconHelper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

public class ManagerPinDialog extends BaseDialog {
    private final PasswordField pinInput = new PasswordField();
    private final Label errorLabel = new Label();

    private String result;

    private ManagerPinDialog(Stage owner) {
        super(owner, 360, 240);
        initializeDialog();
    }

    public static String showDialog(Stage owner) {
        ManagerPinDialog dialog = new ManagerPinDialog(owner);
        dialog.showAndWait();
        return dialog.result;
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(10);

        FontIcon icon = UiIconHelper.makeIcon("mdi2l-lock", 18, "#6B2D1A");
        Label title = new Label("PIN administrateur");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox titleRow = new HBox(8, icon, title);

        Label subtitle = new Label("Veuillez confirmer l'opération");
        subtitle.getStyleClass().add("text-muted");

        pinInput.setPromptText("Saisissez le PIN");
        pinInput.setPrefHeight(48);

        errorLabel.getStyleClass().add("danger");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button cancel = new Button("Annuler");
        cancel.getStyleClass().addAll("button", "elevated");
        cancel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        Button confirm = new Button("Valider");
        confirm.getStyleClass().addAll("button", "success");
        confirm.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        confirm.setOnAction(evt -> confirmPin());

        actions.getChildren().addAll(cancel, confirm);

        root.getChildren().addAll(titleRow, subtitle, pinInput, errorLabel, spacer, actions);

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        result = null;
                        close();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ENTER) {
                        confirmPin();
                        event.consume();
                    }
                });
            }
        });

        return root;
    }

    private void confirmPin() {
        String value = pinInput.getText() == null ? "" : pinInput.getText().trim();
        if (value.isEmpty()) {
            errorLabel.setText("PIN requis");
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
            return;
        }
        result = value;
        close();
    }
}
