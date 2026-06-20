package com.cafepos.util;

import java.util.Locale;
import java.util.Optional;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.UserDAO;
import com.cafepos.model.AppAction;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.AdminSessionManager;
import com.cafepos.service.SessionManager;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

public class ActionAccessManager {
    private static final String ACTION_ROLE_KEY_PREFIX = "action.role.";
    private static final String ACTION_PIN_KEY_PREFIX = "action.pin.";

    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final UserDAO userDAO = new UserDAO();

    public boolean ensureAccess(AppAction action, Window owner) {
        UserRole requiredRole = resolveRequiredRole(action);
        boolean pinRequired = resolvePinRequired(action);
        User current = SessionManager.getCurrentUser();

        // Temporary manager elevation is orthogonal to cashier identity.
        if (requiredRole == UserRole.MANAGER && AdminSessionManager.isAdminUnlocked()) {
            return true;
        }

        if (!pinRequired) {
            // No PIN required: role check is the only gate.
            if (current == null && requiredRole == UserRole.BARISTA) {
                return true;
            }
            if (current != null && isRoleAllowed(current.getRole(), requiredRole)) {
                return true;
            }
            // Role insufficient and PIN not required → deny without prompting.
            showWarning("Acces refuse", "Role requis: " + requiredRole.name());
            return false;
        }

        // PIN is required → always prompt for a PIN with the required role.
        return requestPin(action, requiredRole, owner);
    }

    public UserRole resolveRequiredRole(AppAction action) {
        String value = readSetting(ACTION_ROLE_KEY_PREFIX + action.getKey());
        if (value == null || value.isBlank()) {
            return action.getDefaultRole();
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return action.getDefaultRole();
        }
    }

    public boolean resolvePinRequired(AppAction action) {
        String value = readSetting(ACTION_PIN_KEY_PREFIX + action.getKey());
        if (value == null || value.isBlank()) {
            return action.isDefaultPinRequired();
        }
        return Boolean.parseBoolean(value.trim());
    }

    public boolean isRoleAllowed(UserRole current, UserRole required) {
        if (current == null || required == null) {
            return false;
        }
        if (current == UserRole.MANAGER) {
            return true;
        }
        return current == required;
    }

    private boolean requestPin(AppAction action, UserRole requiredRole, Window owner) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Acces action");
        dialog.setHeaderText("PIN requis: " + action.getLabel());
        dialog.setContentText("PIN:");
        if (owner != null) {
            dialog.initOwner(owner);
        }
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
            String hash = SecurityUtils.sha256Hex(pin);
            User user = userDAO.findByPinAndMinRole(hash, requiredRole);
            if (user == null) {
                showWarning("Acces refuse", "PIN invalide.");
                return false;
            }
            if (requiredRole == UserRole.MANAGER) {
                AdminSessionManager.unlock();
            }
            return true;
        } catch (Exception ex) {
            showWarning("Erreur", "Verification PIN impossible.");
            return false;
        }
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private String readSetting(String key) {
        try {
            return settingsDAO.getValue(key);
        } catch (Exception ex) {
            return null;
        }
    }
}
