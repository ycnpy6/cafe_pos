package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.model.AppAction;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cafepos.model.User;
import com.cafepos.service.SessionManager;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.ActionAccessManager;
import com.cafepos.util.WindowUtils;

public class BackOfficeController {
    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeController.class);
    private static final String REPORTS_VIEW = "/com/cafepos/fxml/reports.fxml";
    private static volatile String initialView;
    private static volatile BackOfficeController current;
    private final ActionAccessManager accessManager = new ActionAccessManager();

    @FXML
    private StackPane contentPane;

    @FXML
    private void initialize() {
        current = this;
        String view = initialView;
        if (view != null && !view.isBlank()) {
            initialView = null;
            if (!loadView(view)) {
                loadView("/com/cafepos/fxml/dashboard.fxml");
            }
        } else {
            loadView("/com/cafepos/fxml/dashboard.fxml");
        }
    }

    public static void setInitialView(String fxml) {
        initialView = fxml;
    }

    public static BackOfficeController getCurrent() {
        return current;
    }

    public boolean navigateTo(String fxml) {
        return loadView(fxml);
    }

    @FXML
    private void onShowDashboard() {
        if (accessManager.ensureAccess(AppAction.OPEN_DASHBOARD, currentWindow())) {
            loadView("/com/cafepos/fxml/dashboard.fxml");
        }
    }

    @FXML
    private void onShowStock() {
        if (accessManager.ensureAccess(AppAction.OPEN_STOCK, currentWindow())) {
            loadView("/com/cafepos/fxml/stock.fxml");
        }
    }

    @FXML
    private void onShowCustomers() {
        if (accessManager.ensureAccess(AppAction.OPEN_CLIENTS, currentWindow())) {
            loadView("/com/cafepos/fxml/clients.fxml");
        }
    }

    @FXML
    private void onShowReports() {
        if (accessManager.ensureAccess(AppAction.OPEN_REPORTS, currentWindow())) {
            loadView(REPORTS_VIEW);
        }
    }

    @FXML
    private void onShowSettings() {
        if (accessManager.ensureAccess(AppAction.OPEN_SETTINGS, currentWindow())) {
            loadView("/com/cafepos/fxml/settings.fxml");
        }
    }

    @FXML
    private void onBackToPos() {
        goBackToPos();
    }

    public void goBackToPos() {
        if (!accessManager.ensureAccess(AppAction.BACK_TO_POS, currentWindow())) {
            return;
        }
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            openLoginScene();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/cafepos/fxml/pos.fxml"),
                    MainApp.getMessages());
            Parent root = loader.load();
            PosController controller = loader.getController();
            controller.setUserInfo(user.getName(), user.getRole().name());
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) contentPane.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
            stage.setIconified(false);
            stage.show();
            stage.toFront();
        } catch (Exception ex) {
            LOG.error("Echec retour POS", ex);
        }
    }

    private void openLoginScene() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/cafepos/fxml/login.fxml"),
                    MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) contentPane.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
            stage.setIconified(false);
            stage.show();
            stage.toFront();
        } catch (Exception ex) {
            LOG.error("Echec ouverture login depuis back-office", ex);
        }
    }

    private boolean loadView(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml), MainApp.getMessages());
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
            return true;
        } catch (Exception ex) {
            LOG.error("Echec chargement view: {}", fxml, ex);
            return false;
        }
    }

    private Stage currentWindow() {
        return contentPane == null || contentPane.getScene() == null
                ? null
                : (Stage) contentPane.getScene().getWindow();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
