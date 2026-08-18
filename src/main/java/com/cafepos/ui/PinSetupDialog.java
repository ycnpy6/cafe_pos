package com.cafepos.ui;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialogue de definition d'un nouveau PIN (saisie puis confirmation), avec le
 * meme pave numerique que PinPromptDialog. Reutilise a la fois pour forcer le
 * remplacement du PIN manager par defaut (1234) au premier lancement, et pour
 * le changement de PIN volontaire depuis Reglages > Utilisateurs.
 */
public final class PinSetupDialog extends BaseDialog {
    private static final int PIN_MAX_LENGTH = 6;
    private static final int PIN_MIN_LENGTH = 4;

    private final String titleText;
    private final String subtitleText;
    private final String forbiddenPin;

    private final StringBuilder pinBuffer = new StringBuilder(PIN_MAX_LENGTH);
    private final List<Label> dots = new ArrayList<>(PIN_MAX_LENGTH);

    private VBox card;
    private Label stepLabel;
    private Label errorLabel;
    private String firstEntry;
    private String result;

    private PinSetupDialog(Stage owner, String title, String subtitle, String forbiddenPin) {
        super(owner, 360, 600);
        this.titleText = title;
        this.subtitleText = subtitle;
        this.forbiddenPin = forbiddenPin;
        initializeDialog();
    }

    /**
     * Shows the two-step PIN setup (enter, then confirm) and blocks until done.
     * Returns the plain new PIN (4-6 digits), or empty if the user cancelled.
     * Kept for the forced first-run PIN-change flow: title defaults to
     * "Nouveau PIN manager" and "1234" stays rejected.
     */
    public static Optional<String> promptNewPin(Stage owner, String subtitle) {
        return promptNewPin(owner, "Nouveau PIN manager", subtitle, "1234");
    }

    /**
     * General-purpose variant used from Settings > Utilisateurs: caller
     * chooses the title and which PIN (if any) must be rejected.
     *
     * @param forbiddenPin PIN value to refuse (e.g. the known default), or
     *                     null/blank to accept any PIN within the length rules.
     */
    public static Optional<String> promptNewPin(Stage owner, String title, String subtitle, String forbiddenPin) {
        PinSetupDialog dialog = new PinSetupDialog(owner, title, subtitle, forbiddenPin);
        dialog.showAndWait();
        return Optional.ofNullable(dialog.result);
    }

    @Override
    protected VBox buildContent() {
        Label title = new Label(titleText == null ? "Nouveau PIN" : titleText);
        title.getStyleClass().add("title-3");

        Label subtitle = new Label(subtitleText == null ? "" : subtitleText);
        subtitle.getStyleClass().add("text-muted");
        subtitle.setStyle("-fx-font-size: 12px;");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(300);
        subtitle.setAlignment(Pos.CENTER);

        stepLabel = new Label("Saisissez un PIN (4 a 6 chiffres)");
        stepLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        HBox dotsRow = new HBox(12);
        dotsRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < PIN_MAX_LENGTH; i++) {
            Label dot = new Label("o");
            dot.getStyleClass().add("pin-dot");
            dots.add(dot);
            dotsRow.getChildren().add(dot);
        }

        GridPane numpad = new GridPane();
        numpad.setHgap(8);
        numpad.setVgap(8);
        numpad.setAlignment(Pos.CENTER);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK"};
        for (int i = 0; i < keys.length; i++) {
            numpad.add(keyButton(keys[i]), i % 3, i / 3);
        }

        errorLabel = new Label("");
        errorLabel.getStyleClass().add("danger");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button cancel = new Button("Plus tard");
        cancel.getStyleClass().addAll("button", "flat");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        card = new VBox(16, title, subtitle, stepLabel, dotsRow, numpad, errorLabel, cancel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8));
        return card;
    }

    private Button keyButton(String key) {
        Button button = new Button("DEL".equals(key) ? "⌫" : key);
        button.getStyleClass().add("button");
        button.getStyleClass().add("DEL".equals(key) ? "danger" : "OK".equals(key) ? "success" : "elevated");
        button.setPrefSize(72, 64);
        button.setOnAction(evt -> onKey(key));
        return button;
    }

    private void onKey(String key) {
        switch (key) {
            case "DEL" -> {
                if (!pinBuffer.isEmpty()) {
                    pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                }
            }
            case "OK" -> onConfirm();
            default -> {
                if (pinBuffer.length() < PIN_MAX_LENGTH) {
                    pinBuffer.append(key);
                }
            }
        }
        renderDots();
    }

    private void onConfirm() {
        if (pinBuffer.length() < PIN_MIN_LENGTH) {
            showError("Code a " + PIN_MIN_LENGTH + " chiffres minimum");
            shake();
            return;
        }
        String entry = pinBuffer.toString();
        if (firstEntry == null) {
            if (forbiddenPin != null && !forbiddenPin.isBlank() && forbiddenPin.equals(entry)) {
                showError("Choisissez un PIN different de " + forbiddenPin);
                pinBuffer.setLength(0);
                renderDots();
                shake();
                return;
            }
            firstEntry = entry;
            pinBuffer.setLength(0);
            renderDots();
            stepLabel.setText("Confirmez le nouveau PIN");
            hideError();
            return;
        }
        if (!firstEntry.equals(entry)) {
            firstEntry = null;
            pinBuffer.setLength(0);
            renderDots();
            stepLabel.setText("Saisissez un PIN (4 a 6 chiffres)");
            showError("Les codes ne correspondent pas");
            shake();
            return;
        }
        result = entry;
        close();
    }

    private void shake() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(70), card);
        shake.setFromX(0);
        shake.setByX(12);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(evt -> card.setTranslateX(0));
        shake.play();
    }

    private void renderDots() {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setText(pinBuffer.length() > i ? "*" : "o");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
