package com.cafepos.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cafepos.model.User;
import com.cafepos.service.SessionManager;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;

public class BackOfficeController {
    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeController.class);
    private static volatile String initialView;

    @FXML
    private StackPane contentPane;

    @FXML
    private void initialize() {
        String view = initialView;
        if (view != null && !view.isBlank()) {
            initialView = null;
            loadView(view);
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
        loadView("/com/cafepos/fxml/reports.fxml");
    }

    @FXML
    private void onShowSettings() {
        loadView("/com/cafepos/fxml/settings.fxml");
    }

    @FXML
    private void onBackToPos() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/pos.fxml"));
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

    private void loadView(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (Exception ex) {
            LOG.error("Echec chargement view: {}", fxml, ex);
        }
    }
}
