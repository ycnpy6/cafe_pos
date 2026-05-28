package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.UserRole;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cafepos.model.User;
import com.cafepos.service.SessionManager;
import com.cafepos.util.SecurityUtils;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;

import java.util.Optional;

public class BackOfficeController {
    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeController.class);
    private static final String REPORTS_VIEW = "/com/cafepos/fxml/reports.fxml";
    private static volatile String initialView;
    private final UserDAO userDAO = new UserDAO();

    @FXML
    private StackPane contentPane;

    @FXML
    private void initialize() {
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

    @FXML
    private void onShowDashboard() {
        loadView("/com/cafepos/fxml/dashboard.fxml");
    }

    @FXML
    private void onShowStock() {
        loadView("/com/cafepos/fxml/stock.fxml");
    }

    @FXML
    private void onShowCustomers() {
        loadView("/com/cafepos/fxml/clients.fxml");
    }

    @FXML
    private void onShowReports() {
        loadView(REPORTS_VIEW);
    }

    @FXML
    private void onShowSettings() {
        loadView("/com/cafepos/fxml/settings.fxml");
    }

    @FXML
    private void onBackToPos() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/cafepos/fxml/pos.fxml"),
                    MainApp.getMessages());
            Parent root = loader.load();
            PosController controller = loader.getController();
            User user = SessionManager.getCurrentUser();
            if (user != null) {
                controller.setUserInfo(user.getName(), user.getRole().name());
            }
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
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

    private boolean loadView(String fxml) {
        try {
            if (REPORTS_VIEW.equals(fxml) && !ensureReportsAccess()) {
                return false;
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml), MainApp.getMessages());
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
            return true;
        } catch (Exception ex) {
            LOG.error("Echec chargement view: {}", fxml, ex);
            return false;
        }
    }

    private boolean ensureReportsAccess() {
        User current = SessionManager.getCurrentUser();
        if (current != null && current.getRole() == UserRole.MANAGER) {
            return true;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Acces rapports");
        dialog.setHeaderText("PIN manager requis pour ouvrir Rapports");
        dialog.setContentText("PIN:");

        Optional<String> input = dialog.showAndWait();
        if (input.isEmpty()) {
            return false;
        }

        String pin = input.get().trim();
        if (pin.isBlank()) {
            showWarning("Acces refuse", "PIN manquant.");
            return false;
        }

        try {
            User manager = userDAO.findByPinAndRole(SecurityUtils.sha256Hex(pin), UserRole.MANAGER);
            if (manager == null) {
                showWarning("Acces refuse", "PIN manager invalide.");
                return false;
            }
            SessionManager.setCurrentUser(manager);
            return true;
        } catch (Exception ex) {
            LOG.error("Echec verification PIN rapports", ex);
            showWarning("Erreur", "Verification du PIN impossible.");
            return false;
        }
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
