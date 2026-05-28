package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.SessionManager;
import com.cafepos.service.WorkPeriodService;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.SecurityUtils;
import com.cafepos.util.WindowUtils;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LaunchController {
    private static final Logger LOG = LoggerFactory.getLogger(LaunchController.class);
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final long LOCKOUT_MS = 30_000;

    private final UserDAO userDAO = new UserDAO();
    private final WorkPeriodService workPeriodService = new WorkPeriodService();

    private final StringBuilder pinBuffer = new StringBuilder(4);

    private Destination pendingDestination;
    private int failedAttempts;
    private long lockUntilMs;

    @FXML
    private VBox pinDialog;
    @FXML
    private VBox pinOverlay;
    @FXML
    private HBox pinDots;
    @FXML
    private Label pinDot1;
    @FXML
    private Label pinDot2;
    @FXML
    private Label pinDot3;
    @FXML
    private Label pinDot4;
    @FXML
    private Label pinError;

    private final List<Label> dynamicPinDots = new ArrayList<>(4);

    @FXML
    private void initialize() {
        if (pinDialog == null) {
            pinDialog = pinOverlay;
        }
        ensurePinDots();
        hidePinDialog();
        renderPinDots();
    }

    @FXML
    private void onCaisseClicked() {
        onOpenCaisse();
    }

    @FXML
    private void onStockClicked() {
        onOpenStock();
    }

    @FXML
    private void onGestionClicked() {
        onOpenGestion();
    }

    @FXML
    private void onSettingsClicked() {
        onOpenSettings();
    }

    @FXML
    private void onOpenCaisse() {
        Task<LaunchContext> task = new Task<>() {
            @Override
            protected LaunchContext call() throws Exception {
                User user = userDAO.findFirstByRole(UserRole.BARISTA);
                if (user == null) {
                    user = userDAO.findFirstByRole(UserRole.MANAGER);
                }
                if (user == null) {
                    throw new IllegalStateException("Aucun utilisateur disponible");
                }
                int workPeriodId = workPeriodService.openIfNeeded(user.getId());
                return new LaunchContext(user, workPeriodId);
            }
        };
        task.setOnSucceeded(evt -> {
            LaunchContext ctx = task.getValue();
            SessionManager.setCurrentUser(ctx.user());
            SessionManager.setCurrentWorkPeriodId(ctx.workPeriodId());
            openPos();
        });
        task.setOnFailed(evt -> {
            LOG.error("Echec ouverture caisse", task.getException());
            showPinError("Ouverture caisse impossible");
        });
        Thread thread = new Thread(task, "launch-caisse");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onOpenStock() {
        openBackOffice("/com/cafepos/fxml/stock.fxml");
    }

    @FXML
    private void onOpenGestion() {
        openBackOffice("/com/cafepos/fxml/dashboard.fxml");
    }

    @FXML
    private void onOpenSettings() {
        openBackOffice("/com/cafepos/fxml/settings.fxml");
    }

    @FXML
    private void onPinDigit(ActionEvent event) {
        if (isLocked()) {
            showPinError(lockMessage());
            return;
        }
        Object source = event.getSource();
        if (!(source instanceof Button button)) {
            return;
        }
        String value = button.getUserData() == null
                ? String.valueOf(button.getText())
                : String.valueOf(button.getUserData());
        if ("⌫".equals(value)) {
            value = "BACK";
        } else if ("✓".equals(value)) {
            value = "OK";
        }
        switch (value) {
            case "BACK" -> {
                if (!pinBuffer.isEmpty()) {
                    pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                }
            }
            case "OK" -> onPinConfirm();
            default -> {
                if (pinBuffer.length() < 4 && value.matches("\\d")) {
                    pinBuffer.append(value);
                }
            }
        }
        renderPinDots();
    }

    @FXML
    private void onPinDelete() {
        if (!pinBuffer.isEmpty()) {
            pinBuffer.deleteCharAt(pinBuffer.length() - 1);
            renderPinDots();
        }
    }

    @FXML
    private void onPinCancel() {
        hidePinDialog();
    }

    @FXML
    private void onPinConfirm() {
        if (pendingDestination == null) {
            hidePinDialog();
            return;
        }
        if (isLocked()) {
            showPinError(lockMessage());
            return;
        }
        if (pinBuffer.length() != 4) {
            showPinError("Code a 4 chiffres requis");
            return;
        }

        String pin = pinBuffer.toString();
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                String hash = SecurityUtils.sha256Hex(pin);
                return userDAO.findByPinAndRole(hash, UserRole.MANAGER);
            }
        };
        task.setOnSucceeded(evt -> {
            User admin = task.getValue();
            if (admin == null) {
                handleWrongPin();
                return;
            }
            failedAttempts = 0;
            SessionManager.setCurrentUser(admin);
            Destination destination = pendingDestination;
            hidePinDialog();
            switch (destination) {
                case STOCK -> openBackOffice("/com/cafepos/fxml/stock.fxml");
                case GESTION -> openBackOffice("/com/cafepos/fxml/dashboard.fxml");
                case SETTINGS -> openBackOffice("/com/cafepos/fxml/settings.fxml");
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur verification PIN", task.getException());
            showPinError("Verification PIN impossible");
        });
        Thread thread = new Thread(task, "launch-pin-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void showPinFor(Destination destination) {
        pendingDestination = destination;
        pinBuffer.setLength(0);
        renderPinDots();
        if (isLocked()) {
            showPinError(lockMessage());
        } else {
            pinError.setVisible(false);
            pinError.setManaged(false);
        }
        pinDialog.setVisible(true);
        pinDialog.setManaged(true);
    }

    private void hidePinDialog() {
        pinDialog.setVisible(false);
        pinDialog.setManaged(false);
        pinBuffer.setLength(0);
        renderPinDots();
    }

    private void renderPinDots() {
        setDot(pinDot1, pinBuffer.length() > 0);
        setDot(pinDot2, pinBuffer.length() > 1);
        setDot(pinDot3, pinBuffer.length() > 2);
        setDot(pinDot4, pinBuffer.length() > 3);
    }

    private void setDot(Label label, boolean filled) {
        if (label == null) {
            return;
        }
        label.setText(filled ? "*" : "o");
    }

    private void ensurePinDots() {
        if (pinDots == null) {
            return;
        }
        if (!pinDots.getChildren().isEmpty() && pinDot1 != null && pinDot2 != null && pinDot3 != null && pinDot4 != null) {
            return;
        }
        pinDots.getChildren().clear();
        dynamicPinDots.clear();
        for (int i = 0; i < 4; i++) {
            Label dot = new Label("o");
            dot.getStyleClass().add("pin-dot");
            dynamicPinDots.add(dot);
            pinDots.getChildren().add(dot);
        }
        pinDot1 = dynamicPinDots.get(0);
        pinDot2 = dynamicPinDots.get(1);
        pinDot3 = dynamicPinDots.get(2);
        pinDot4 = dynamicPinDots.get(3);
    }

    private void handleWrongPin() {
        failedAttempts++;
        pinBuffer.setLength(0);
        renderPinDots();

        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            lockUntilMs = System.currentTimeMillis() + LOCKOUT_MS;
            showPinError(lockMessage());
        } else {
            showPinError("PIN incorrect");
        }
        shakePinDialog();
    }

    private void shakePinDialog() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(70), pinDialog);
        shake.setFromX(0);
        shake.setByX(12);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(evt -> pinDialog.setTranslateX(0));
        shake.play();
    }

    private void showPinError(String message) {
        pinError.setText(message == null ? "" : message);
        pinError.setVisible(true);
        pinError.setManaged(true);
    }

    private boolean isLocked() {
        return System.currentTimeMillis() < lockUntilMs;
    }

    private String lockMessage() {
        long remaining = Math.max(1, (lockUntilMs - System.currentTimeMillis() + 999) / 1000);
        return "Bloque " + remaining + "s";
    }

    private void openPos() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/pos.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            PosController controller = loader.getController();
            User user = SessionManager.getCurrentUser();
            if (user != null) {
                controller.setUserInfo(user.getName(), user.getRole().name());
            }
            com.cafepos.model.Order locked = SessionManager.consumeLockedOrder();
            if (locked != null) {
                controller.restoreOrder(locked);
            }
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) pinDialog.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture POS", ex);
            showPinError("Ouverture POS impossible");
        }
    }

    private void openBackOffice(String initialView) {
        try {
            BackOfficeController.setInitialView(initialView);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/backoffice.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) pinDialog.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture back-office", ex);
            showPinError("Ouverture back-office impossible");
        }
    }

    private enum Destination {
        STOCK,
        GESTION,
        SETTINGS
    }

    private record LaunchContext(User user, int workPeriodId) {
    }
}
