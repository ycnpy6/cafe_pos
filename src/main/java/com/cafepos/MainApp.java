package com.cafepos;

import com.cafepos.dao.SettingsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cafepos.db.DatabaseManager;
import com.cafepos.service.AdminSessionManager;
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
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.net.URL;

public class MainApp extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);
    private static final String APP_LANGUAGE_KEY = "app.language";
    private static final String APP_CSS_PATH = "/com/cafepos/styles/app.css";
    private static final String BRAND_CSS_PATH = "/com/cafepos/css/common-grounds.css";
    private static final String APP_ICON_PHOTO_PATH = "/photo_2026-05-31_13-56-31.jpg";
    private static ResourceBundle messages;
    private static Locale appLocale;
    private static Image appIcon;
    private static FileLock instanceLock;
    private static FileChannel lockChannel;
    private static Path instanceMarkerFile;
    private static Path focusTriggerFile;
    private static long appStartMillis;
    private static volatile boolean primaryInstance;

    @Override
    public void start(Stage stage) {
        if (!ensureSingleInstance()) {
            Platform.exit();
            return;
        }
        if (appStartMillis <= 0) {
            appStartMillis = System.currentTimeMillis();
        }
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Scene splashScene = new Scene(buildSplashView(), 520, 320);
        applyBrandTheme(splashScene);
        stage.setTitle(text("app.name", "Cafe POS"));
        applyAppIcon(stage);
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.setMaximized(true);
        stage.iconifiedProperty().addListener((obs, wasIconified, isIconified) -> {
            if (isIconified) {
                AdminSessionManager.lock();
            }
        });
        stage.setOnCloseRequest(event -> {
            long uptimeMs = System.currentTimeMillis() - appStartMillis;
            if (uptimeMs < 4_000) {
                LOG.warn("Fermeture precoce ignoree ({} ms)", uptimeMs);
                event.consume();
            }
        });
        stage.setScene(splashScene);
        stage.show();
        if (primaryInstance) {
            startFocusTriggerWatcher(stage);
        }

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
        AdminSessionManager.lock();
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

    public static void applyAppIcon(Stage stage) {
        if (stage == null) {
            return;
        }
        Image icon = loadAppIcon();
        if (icon == null) {
            return;
        }
        stage.getIcons().setAll(icon);
    }

    private static Image loadAppIcon() {
        if (appIcon != null) {
            return appIcon;
        }

        String[] candidates = {
                APP_ICON_PHOTO_PATH,
                "/com/cafepos/images/logo.png",
                "/com/cafepos/images/logo.jpg"
        };

        for (String candidate : candidates) {
            URL resource = MainApp.class.getResource(candidate);
            if (resource != null) {
                appIcon = new Image(resource.toExternalForm(), false);
                return appIcon;
            }
        }

        LOG.warn("Aucune icone application trouvee dans les resources.");
        return null;
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

    private void startFocusTriggerWatcher(Stage stage) {
        if (stage == null || focusTriggerFile == null) {
            return;
        }
        Path parent = focusTriggerFile.getParent();
        if (parent == null) {
            return;
        }

        Thread watcherThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Files.createDirectories(parent);
                parent.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.take();
                    boolean shouldBringFront = false;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        Object context = event.context();
                        if (!(context instanceof Path changedPath)) {
                            continue;
                        }
                        if (changedPath.getFileName().equals(focusTriggerFile.getFileName())) {
                            shouldBringFront = true;
                        }
                    }

                    if (shouldBringFront && isNewTrigger()) {
                        Platform.runLater(() -> {
                            if (stage.isIconified()) {
                                stage.setIconified(false);
                            }
                            stage.toFront();
                            stage.requestFocus();
                        });
                        try {
                            Files.deleteIfExists(focusTriggerFile);
                        } catch (Exception ex) {
                            LOG.debug("Suppression focus.trigger ignoree", ex);
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                LOG.warn("Surveillance focus.trigger indisponible", ex);
            }
        }, "focus-trigger-watch");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private boolean isNewTrigger() {
        if (focusTriggerFile == null || !Files.exists(focusTriggerFile)) {
            return false;
        }
        try {
            String raw = Files.readString(focusTriggerFile, StandardCharsets.UTF_8);
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                return true;
            }
            long triggerTime = Long.parseLong(value);
            return triggerTime >= appStartMillis;
        } catch (Exception ex) {
            return true;
        }
    }

    private static Path resolveAppDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".CafePOS");
        }
        return Paths.get(appData, "CafePOS");
    }

    private static void releaseInstanceLock() {
        try {
            if (instanceLock != null && instanceLock.isValid()) {
                instanceLock.release();
            }
        } catch (Exception ignored) {
        }
        try {
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (instanceMarkerFile != null) {
                Files.deleteIfExists(instanceMarkerFile);
            }
        } catch (Exception ignored) {
        }
        instanceLock = null;
        lockChannel = null;
        instanceMarkerFile = null;
        primaryInstance = false;
    }

    private static boolean isAnotherMainAppProcessRunning() {
        try {
            long currentPid = ProcessHandle.current().pid();
            return ProcessHandle.allProcesses()
                    .filter(handle -> handle.pid() != currentPid)
                    .map(ProcessHandle::info)
                    .map(ProcessHandle.Info::commandLine)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .anyMatch(command -> command.contains("com.cafepos.MainApp"));
        } catch (Exception ex) {
            LOG.debug("Detection process-level singleton indisponible", ex);
            return false;
        }
    }

    private static synchronized boolean ensureSingleInstance() {
        if (primaryInstance && instanceLock != null && instanceLock.isValid()) {
            return true;
        }

        if (appStartMillis <= 0) {
            appStartMillis = System.currentTimeMillis();
        }

        try {
            Path appDataDir = resolveAppDataDir();
            Files.createDirectories(appDataDir);

            Path lockFile = appDataDir.resolve("app.lock");
            instanceMarkerFile = appDataDir.resolve("app.instance");
            focusTriggerFile = appDataDir.resolve("focus.trigger");

            boolean markerAcquired = tryAcquireInstanceMarker(instanceMarkerFile);
            if (!markerAcquired) {
                Files.writeString(
                        focusTriggerFile,
                        String.valueOf(System.currentTimeMillis()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                return false;
            }

            if (isAnotherMainAppProcessRunning()) {
                Files.writeString(
                        focusTriggerFile,
                        String.valueOf(System.currentTimeMillis()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                Files.deleteIfExists(instanceMarkerFile);
                return false;
            }

            if (lockChannel == null || !lockChannel.isOpen()) {
                lockChannel = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
            }

            try {
                instanceLock = lockChannel.tryLock();
            } catch (OverlappingFileLockException ex) {
                instanceLock = null;
            }

            if (instanceLock == null) {
                Files.deleteIfExists(instanceMarkerFile);
                Files.writeString(
                        focusTriggerFile,
                        String.valueOf(System.currentTimeMillis()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                return false;
            }

            primaryInstance = true;
            return true;
        } catch (Exception ex) {
            LOG.warn("Verrou instance indisponible, lancement refuse", ex);
            try {
                if (instanceMarkerFile != null) {
                    Files.deleteIfExists(instanceMarkerFile);
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private static boolean tryAcquireInstanceMarker(Path markerPath) {
        if (markerPath == null) {
            return false;
        }
        String currentPid = String.valueOf(ProcessHandle.current().pid());
        try {
            Files.writeString(
                    markerPath,
                    currentPid,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            return true;
        } catch (FileAlreadyExistsException exists) {
            try {
                String raw = Files.readString(markerPath, StandardCharsets.UTF_8).trim();
                long existingPid = Long.parseLong(raw);
                boolean alive = ProcessHandle.of(existingPid).map(ProcessHandle::isAlive).orElse(false);
                if (alive && existingPid != ProcessHandle.current().pid()) {
                    return false;
                }
                Files.deleteIfExists(markerPath);
                Files.writeString(
                        markerPath,
                        currentPid,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
                return true;
            } catch (Exception ex) {
                LOG.debug("Marqueur instance existant illisible", ex);
                return false;
            }
        } catch (Exception ex) {
            LOG.warn("Impossible de creer le marqueur instance", ex);
            return false;
        }
    }

    @Override
    public void stop() throws Exception {
        releaseInstanceLock();
        super.stop();
    }

    public static void main(String[] args) {
        if (!ensureSingleInstance()) {
            releaseInstanceLock();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(MainApp::releaseInstanceLock, "app-lock-shutdown"));
        launch(args);
    }
}
