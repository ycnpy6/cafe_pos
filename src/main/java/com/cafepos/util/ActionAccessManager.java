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
import com.cafepos.ui.PinPromptDialog;

import javafx.stage.Stage;
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
        if (requiredRole == UserRole.MANAGER && AdminSessionManager.isAdminUnlocked() && !pinRequired) {
            return true;
        }

        boolean alreadyAuthorized = (current == null && requiredRole == UserRole.BARISTA)
                || (current != null && isRoleAllowed(current.getRole(), requiredRole));

        if (alreadyAuthorized && !pinRequired) {
            // Already the right role and no forced re-auth requested: let it through.
            return true;
        }

        // Either the role is insufficient (no matter the PIN setting) or a forced
        // re-auth was requested: offer a PIN step-up rather than a flat denial,
        // so a manager-gated screen is always reachable, never a dead end.
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
        Stage ownerStage = owner instanceof Stage stage ? stage : null;
        Optional<User> authorized = PinPromptDialog.promptForRole(ownerStage, requiredRole, action.getLabel(), userDAO);
        if (authorized.isEmpty()) {
            return false;
        }
        if (requiredRole == UserRole.MANAGER) {
            AdminSessionManager.unlock();
        }
        return true;
    }

    private String readSetting(String key) {
        try {
            return settingsDAO.getValue(key);
        } catch (Exception ex) {
            return null;
        }
    }
}
