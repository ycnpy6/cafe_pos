package com.cafepos;

import atlantafx.base.theme.PrimerDark;
import com.cafepos.db.DatabaseManager;
import com.cafepos.util.AppScheduler;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        Scene splashScene = new Scene(buildSplashView(), 520, 320);
        stage.setTitle("Cafe POS");
        stage.setScene(splashScene);
        stage.show();

        // Initialisation DB en arriere-plan pour ne pas bloquer le thread FX.
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                DatabaseManager.initialize();
                AppScheduler.start();
                return null;
            }
        };

        initTask.setOnSucceeded(event -> {
            try {
                IdleMonitor.start(() -> {
                    try {
                        loadLoginScene(stage);
                    } catch (Exception ex) {
                        LOG.error("Erreur retour login", ex);
                    }
                });
                loadLoginScene(stage);
            } catch (Exception ex) {
                LOG.error("Erreur au chargement de login.fxml", ex);
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
        Label title = new Label("Cafe POS");
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

    private void loadLoginScene(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/cafepos/fxml/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 820, 520);
        scene.getStylesheets().add(MainApp.class.getResource("/com/cafepos/styles/app.css").toExternalForm());
        IdleMonitor.bindScene(scene);
        stage.setScene(scene);
        WindowUtils.applyFullSize(stage);
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
