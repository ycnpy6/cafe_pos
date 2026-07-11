package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.model.AppAction;
import com.cafepos.service.AdminSessionManager;
import com.cafepos.service.SessionManager;
import com.cafepos.util.ActionAccessManager;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;

public class LaunchController {
    private static final Logger LOG = LoggerFactory.getLogger(LaunchController.class);

    private final ActionAccessManager accessManager = new ActionAccessManager();

    @FXML
    private StackPane brandLogoContainer;
    @FXML
    private javafx.scene.control.Label appTitle;

    @FXML
    private void initialize() {
        applyLaunchBranding();
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
        if (!accessManager.ensureAccess(AppAction.OPEN_POS, currentWindow())) {
            return;
        }
        // Start POS with an explicit user login every time.
        // This avoids the old auto-barista fallback and prevents accidental
        // sales attribution to the wrong role.
        SessionManager.setCurrentUser(null);
        SessionManager.setCurrentWorkPeriodId(null);
        AdminSessionManager.lock();
        openLogin();
    }

    @FXML
    private void onOpenStock() {
        if (!accessManager.ensureAccess(AppAction.OPEN_STOCK, currentWindow())) {
            return;
        }
        openBackOffice("/com/cafepos/fxml/stock.fxml");
    }

    @FXML
    private void onOpenGestion() {
        if (!accessManager.ensureAccess(AppAction.OPEN_DASHBOARD, currentWindow())) {
            return;
        }
        openBackOffice("/com/cafepos/fxml/dashboard.fxml");
    }

    @FXML
    private void onOpenSettings() {
        if (!accessManager.ensureAccess(AppAction.OPEN_SETTINGS, currentWindow())) {
            return;
        }
        openBackOffice("/com/cafepos/fxml/settings.fxml");
    }

    private Stage currentWindow() {
        return brandLogoContainer == null || brandLogoContainer.getScene() == null
                ? null
                : (Stage) brandLogoContainer.getScene().getWindow();
    }

    private void applyLaunchBranding() {
        if (appTitle != null) {
            String title = MainApp.text("app.name", "Common Grounds");
            appTitle.setText(title.toUpperCase(Locale.ROOT));
        }
        if (brandLogoContainer != null) {
            brandLogoContainer.getChildren().setAll(buildBrandLogo());
        }
    }

    private Node buildBrandLogo() {
        URL logoUrl = getClass().getResource("/com/cafepos/images/commongrounds.png");
        if (logoUrl != null) {
            ImageView imageView = new ImageView(new Image(logoUrl.toExternalForm(), true));
            imageView.setFitWidth(160);
            imageView.setFitHeight(160);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setClip(new Circle(80, 80, 80));

            Circle ring = new Circle(80);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(Color.web("#6B2D1A"));
            ring.setStrokeWidth(2);

            StackPane wrapper = new StackPane(imageView, ring);
            wrapper.setPrefSize(160, 160);
            return wrapper;
        }

        Circle outer = new Circle(80);
        outer.setFill(Color.web("#F5ECD7"));
        outer.setStroke(Color.web("#6B2D1A"));
        outer.setStrokeWidth(2);

        SVGPath drop = new SVGPath();
        drop.setContent("M 0 -44 C -22 -20 -24 2 -24 18 C -24 38 -12 54 0 58 C 12 54 24 38 24 18 C 24 2 22 -20 0 -44 Z");
        drop.setFill(Color.web("#6B2D1A"));

        Circle inner = new Circle(8);
        inner.setFill(Color.web("#F5ECD7"));
        inner.setTranslateY(18);

        StackPane icon = new StackPane(drop, inner);
        icon.setPrefSize(120, 120);

        StackPane wrapper = new StackPane(outer, icon);
        wrapper.setPrefSize(160, 160);
        return wrapper;
    }

    private void openLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/login.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = currentWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture login", ex);
        }
    }

    private void openBackOffice(String initialView) {
        try {
            BackOfficeController.setInitialView(initialView);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/backoffice.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = currentWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture back-office", ex);
        }
    }
}
