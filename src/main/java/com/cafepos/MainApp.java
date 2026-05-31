package com.cafepos;

import com.cafepos.dao.SettingsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cafepos.db.DatabaseManager;
import com.cafepos.util.AppScheduler;
import com.cafepos.util.BackupService;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.net.URL;

public class MainApp extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);
    private static final String APP_LANGUAGE_KEY = "app.language";
    private static final String APP_CSS_PATH = "/com/cafepos/styles/app.css";
    private static final String BRAND_CSS_PATH = "/com/cafepos/css/common-grounds.css";
    private static ResourceBundle messages;
    private static Locale appLocale;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Scene splashScene = new Scene(buildSplashView(), 520, 320);
        applyBrandTheme(splashScene);
        stage.setTitle(text("app.name", "Cafe POS"));
        stage.setScene(splashScene);
        stage.show();

        // Initialisation DB en arriere-plan pour ne pas bloquer le thread FX.
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    BackupService.applyPendingRestoreIfAny();
                } catch (Exception ex) {
                    LOG.error("Echec application restauration en attente", ex);
                }
                DatabaseManager.initialize();
                applySavedLocaleFromSettings();
                AppScheduler.start();
                return null;
            }
        };

        initTask.setOnSucceeded(event -> {
            try {
                IdleMonitor.start(() -> {
                    try {
                        loadLaunchScene(stage);
                    } catch (Exception ex) {
                        LOG.error("Erreur retour launch", ex);
                    }
                });
                loadLaunchScene(stage);
            } catch (Exception ex) {
                LOG.error("Erreur au chargement de launch.fxml", ex);
                showErrorAndExit("Echec du chargement de l'interface.", ex);
            }
        });

        initTask.setOnFailed(event -> {
            Throwable ex = initTask.getException();
            LOG.error("Echec de l'initialisation DB", ex);
            showErrorAndExit("Echec de l'initialisation DB.", ex);
        });

        Thread initThread = new Thread(initTask, "db-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    private Parent buildSplashView() {
        Label title = new Label(text("app.name", "Cafe POS"));
        title.getStyleClass().addAll("title-2", "text-emphasis");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(64, 64);

        BorderPane pane = new BorderPane();
        pane.setCenter(new StackPane(progress));
        pane.setTop(new StackPane(title));
        BorderPane.setAlignment(title, javafx.geometry.Pos.CENTER);
        BorderPane.setAlignment(progress, javafx.geometry.Pos.CENTER);
        pane.setPrefSize(520, 320);
        return pane;
    }

    private void loadLaunchScene(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/cafepos/fxml/launch.fxml"), getMessages());
        Parent root = loader.load();
        Scene scene = new Scene(root, 1280, 800);
        applyBrandTheme(scene);
        IdleMonitor.bindScene(scene);
        stage.setScene(scene);
        WindowUtils.applyFullSize(stage);
    }

    public static void applyBrandTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        addStylesheetIfMissing(scene, APP_CSS_PATH);
        addStylesheetIfMissing(scene, BRAND_CSS_PATH);
    }

    private static void addStylesheetIfMissing(Scene scene, String resourcePath) {
        URL resource = MainApp.class.getResource(resourcePath);
        if (resource == null) {
            LOG.warn("Feuille CSS introuvable: {}", resourcePath);
            return;
        }
        String externalForm = resource.toExternalForm();
        if (!scene.getStylesheets().contains(externalForm)) {
            scene.getStylesheets().add(externalForm);
        }
    }

    public static ResourceBundle getMessages() {
        if (messages == null) {
            Locale locale = appLocale == null ? Locale.getDefault() : appLocale;
            try {
                messages = ResourceBundle.getBundle("i18n.messages", locale);
            } catch (MissingResourceException ex) {
                messages = ResourceBundle.getBundle("i18n.messages", Locale.FRENCH);
            }
        }
        return messages;
    }

    public static void setAppLocale(Locale locale) {
        if (locale == null) {
            return;
        }
        appLocale = locale;
        Locale.setDefault(locale);
        messages = null;
    }

    public static Locale localeFromCode(String code) {
        if (code == null) {
            return Locale.FRENCH;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("en")) {
            return Locale.ENGLISH;
        }
        return Locale.FRENCH;
    }

    private static void applySavedLocaleFromSettings() {
        try {
            SettingsDAO settingsDAO = new SettingsDAO();
            String savedLanguage = settingsDAO.getValue(APP_LANGUAGE_KEY);
            if (savedLanguage != null && !savedLanguage.isBlank()) {
                setAppLocale(localeFromCode(savedLanguage));
            }
        } catch (Exception ex) {
            LOG.warn("Chargement langue enregistree impossible", ex);
        }
    }

    public static String text(String key, String fallback) {
        try {
            String value = getMessages().getString(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void showErrorAndExit(String message, Throwable ex) {
        // Affichage simple et sortie propre si l'initialisation echoue.
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(message);
        alert.setContentText(ex == null ? "" : String.valueOf(ex.getMessage()));
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
