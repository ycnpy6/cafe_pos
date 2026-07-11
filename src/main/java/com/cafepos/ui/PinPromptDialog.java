package com.cafepos.ui;

import com.cafepos.MainApp;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.util.SecurityUtils;

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
 * Branded PIN step-up dialog shared by every screen gated through
 * ActionAccessManager. Ports the dot-indicator/numpad/shake/lockout pattern
 * that used to live only on the Launch screen so the same UX shows up
 * everywhere a PIN can be requested.
 */
public final class PinPromptDialog extends BaseDialog {
    // Aligne sur LoginController (PIN_MAX_LEN = 6, pas de minimum impose a la
    // creation) : exiger exactement 6 chiffres ici bloquait tout PIN plus court.
    private static final int PIN_LENGTH = 6;
    private static final int PIN_MIN_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_MS = 30_000;

    private final UserRole requiredRole;
    private final String actionLabel;
    private final UserDAO userDAO;

    private final StringBuilder pinBuffer = new StringBuilder(PIN_LENGTH);
    private final List<Label> dots = new ArrayList<>(PIN_LENGTH);

    private VBox card;
    private Label errorLabel;
    private int failedAttempts;
    private long lockUntilMs;
    private User result;

    private PinPromptDialog(Stage owner, UserRole requiredRole, String actionLabel, UserDAO userDAO) {
        super(owner, 360, 560);
        this.requiredRole = requiredRole;
        this.actionLabel = actionLabel;
        this.userDAO = userDAO;
        initializeDialog();
    }

    /**
     * Shows the PIN pad and blocks until the user confirms, cancels, or closes it.
     * Returns the authenticated user (whose role satisfies requiredRole), or empty if cancelled.
     */
    public static Optional<User> promptForRole(Stage owner, UserRole requiredRole, String actionLabel, UserDAO userDAO) {
        PinPromptDialog dialog = new PinPromptDialog(owner, requiredRole, actionLabel, userDAO);
        dialog.showAndWait();
        return Optional.ofNullable(dialog.result);
    }

    @Override
    protected VBox buildContent() {
        Label title = new Label(MainApp.text("pin.title", "Code administrateur"));
        title.getStyleClass().add("title-3");

        Label subtitle = new Label(actionLabel == null ? "" : actionLabel);
        subtitle.getStyleClass().add("text-muted");
        subtitle.setStyle("-fx-font-size: 12px;");

        HBox dotsRow = new HBox(12);
        dotsRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < PIN_LENGTH; i++) {
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

        Button cancel = new Button(MainApp.text("pin.cancel", "Annuler"));
        cancel.getStyleClass().addAll("button", "flat");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(evt -> {
            result = null;
            close();
        });

        card = new VBox(16, title, subtitle, dotsRow, numpad, errorLabel, cancel);
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
        if (isLocked()) {
            showError(lockMessage());
            return;
        }
        switch (key) {
            case "DEL" -> {
                if (!pinBuffer.isEmpty()) {
                    pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                }
            }
            case "OK" -> onConfirm();
            default -> {
                if (pinBuffer.length() < PIN_LENGTH) {
                    pinBuffer.append(key);
                }
            }
        }
        renderDots();
    }

    private void onConfirm() {
        if (isLocked()) {
            showError(lockMessage());
            return;
        }
        if (pinBuffer.length() < PIN_MIN_LENGTH) {
            showError("Code a " + PIN_MIN_LENGTH + " chiffres minimum");
            return;
        }
        try {
            String hash = SecurityUtils.sha256Hex(pinBuffer.toString());
            User user = userDAO.findByPinAndMinRole(hash, requiredRole);
            if (user == null) {
                handleWrongPin();
                return;
            }
            failedAttempts = 0;
            result = user;
            close();
        } catch (Exception ex) {
            showError("Verification PIN impossible");
        }
    }

    private void handleWrongPin() {
        failedAttempts++;
        pinBuffer.setLength(0);
        renderDots();
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockUntilMs = System.currentTimeMillis() + LOCKOUT_MS;
            showError(lockMessage());
        } else {
            showError(MainApp.text("pin.error", "PIN incorrect"));
        }
        shake();
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

    private boolean isLocked() {
        return System.currentTimeMillis() < lockUntilMs;
    }

    private String lockMessage() {
        long remaining = Math.max(1, (lockUntilMs - System.currentTimeMillis() + 999) / 1000);
        return "Bloque " + remaining + "s";
    }
}
