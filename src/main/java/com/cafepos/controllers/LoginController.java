package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.AdminSessionManager;
import com.cafepos.service.SessionManager;
import com.cafepos.service.WorkPeriodService;
import com.cafepos.util.SecurityUtils;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LoginController {
    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);
    private static final int PIN_MAX_LEN = 6;

    private final UserDAO userDAO = new UserDAO();
    private final WorkPeriodService workPeriodService = new WorkPeriodService();

    @FXML
    private BorderPane root;
    @FXML
    private PasswordField pinField;
    @FXML
    private ToggleGroup roleGroup;
    @FXML
    private ComboBox<User> userCombo;
    @FXML
    private Label statusLabel;
    @FXML
    private Button loginButton;

    private volatile boolean busy;

    @FXML
    private void initialize() {
        // Choix par defaut si rien n'est selectionne.
        if (roleGroup != null && roleGroup.getSelectedToggle() == null && !roleGroup.getToggles().isEmpty()) {
            roleGroup.selectToggle(roleGroup.getToggles().get(0));
        }
        configureUserCombo();
        if (roleGroup != null) {
            roleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> loadUsersForSelectedRole());
        }
        loadUsersForSelectedRole();
        showStatus("");
    }

    @FXML
    private void onDigit(ActionEvent event) {
        if (busy) {
            return;
        }
        Button source = (Button) event.getSource();
        appendDigit(source.getText());
    }

    @FXML
    private void onClear(ActionEvent event) {
        if (busy) {
            return;
        }
        pinField.setText("");
        showStatus("");
    }

    @FXML
    private void onBackspace(ActionEvent event) {
        if (busy) {
            return;
        }
        String current = pinField.getText();
        if (!current.isEmpty()) {
            pinField.setText(current.substring(0, current.length() - 1));
        }
    }

    @FXML
    private void onLogin(ActionEvent event) {
        if (busy) {
            return;
        }
        String pin = pinField.getText();
        String role = getSelectedRole();
        User selectedUser = userCombo == null ? null : userCombo.getSelectionModel().getSelectedItem();

        UserRole selectedRole;
        try {
            selectedRole = role == null ? null : UserRole.valueOf(role.trim().toUpperCase());
        } catch (Exception ex) {
            selectedRole = null;
        }

        if (selectedRole == null) {
            showStatus("Choisissez un role.");
            return;
        }
        if (selectedUser == null) {
            showStatus("Choisissez un utilisateur.");
            return;
        }
        if (selectedUser.getRole() != selectedRole) {
            showStatus("Utilisateur incompatible avec le role choisi.");
            return;
        }
        if (selectedRole == UserRole.MANAGER && (pin == null || pin.isBlank())) {
            showStatus("PIN manager requis.");
            return;
        }

        setBusy(true);
        showStatus("Verification...");

        // Verification en arriere-plan pour ne pas bloquer l'interface.
        Task<LoginContext> authTask = new Task<>() {
            @Override
            protected LoginContext call() throws Exception {
                return authenticate(pin, selectedUser);
            }
        };

        authTask.setOnSucceeded(evt -> {
            LoginContext context = authTask.getValue();
            if (context != null && context.user != null) {
                // A new cashier login must reset any transient manager unlock.
                AdminSessionManager.lock();
                SessionManager.setCurrentUser(context.user);
                SessionManager.setCurrentWorkPeriodId(context.workPeriodId);
                loadPosScreen(context.user.getName(), context.user.getRole().name());
            } else {
                showStatus("Connexion invalide (PIN/role).");
            }
            pinField.setText("");
            setBusy(false);
        });

        authTask.setOnFailed(evt -> {
            Throwable ex = authTask.getException();
            LOG.error("Erreur auth", ex);
            showStatus("Erreur de connexion.");
            setBusy(false);
        });

        Thread thread = new Thread(authTask, "login-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void appendDigit(String digit) {
        if (digit == null || digit.isBlank()) {
            return;
        }
        String current = pinField.getText();
        if (current.length() >= PIN_MAX_LEN) {
            return;
        }
        pinField.setText(current + digit);
    }

    private String getSelectedRole() {
        if (roleGroup == null) {
            return null;
        }
        Toggle toggle = roleGroup.getSelectedToggle();
        if (toggle == null || toggle.getUserData() == null) {
            return null;
        }
        return String.valueOf(toggle.getUserData());
    }

    private LoginContext authenticate(String pin, User selectedUser) throws Exception {
        UserRole selectedRole = selectedUser.getRole();
        User user;

        // Operational mode: barista can start a cashier session without a PIN.
        // Manager role remains PIN-protected.
        if (selectedRole == UserRole.BARISTA && (pin == null || pin.isBlank())) {
            user = selectedUser;
        } else {
            String pinHash = SecurityUtils.sha256Hex(pin == null ? "" : pin);
            user = userDAO.findByIdAndPin(selectedUser.getId(), pinHash);
        }

        if (user == null) {
            return null;
        }
        int workPeriodId = workPeriodService.openIfNeeded(user.getId());
        return new LoginContext(user, workPeriodId);
    }

    private void configureUserCombo() {
        if (userCombo == null) {
            return;
        }
        userCombo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        userCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
    }

    private void loadUsersForSelectedRole() {
        if (userCombo == null) {
            return;
        }
        String role = getSelectedRole();
        UserRole selectedRole;
        try {
            selectedRole = role == null ? null : UserRole.valueOf(role.trim().toUpperCase());
        } catch (Exception ex) {
            selectedRole = null;
        }
        if (selectedRole == null) {
            userCombo.getItems().clear();
            return;
        }

        try {
            userCombo.setItems(FXCollections.observableArrayList(userDAO.findByRole(selectedRole)));
            if (!userCombo.getItems().isEmpty()) {
                userCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception ex) {
            LOG.error("Erreur chargement utilisateurs login", ex);
            userCombo.getItems().clear();
            showStatus("Chargement utilisateurs impossible.");
        }
    }

    private static class LoginContext {
        private final User user;
        private final int workPeriodId;

        private LoginContext(User user, int workPeriodId) {
            this.user = user;
            this.workPeriodId = workPeriodId;
        }
    }

    private void loadPosScreen(String username, String role) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/cafepos/fxml/pos.fxml"),
                    MainApp.getMessages());
            Parent root = loader.load();
            PosController controller = loader.getController();
            controller.setUserInfo(username, role);
            com.cafepos.model.Order locked = SessionManager.consumeLockedOrder();
            if (locked != null) {
                controller.restoreOrder(locked);
            }

            Scene scene = new Scene(root, 1024, 640);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) pinField.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec chargement pos.fxml", ex);
            showAlert("Erreur", "Echec chargement de l'ecran POS.");
        }
    }

    private void showStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        if (root != null) {
            root.setDisable(busy);
        }
        if (loginButton != null) {
            loginButton.setDisable(busy);
        }
    }
}
