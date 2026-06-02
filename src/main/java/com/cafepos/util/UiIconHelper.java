package com.cafepos.util;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

public final class UiIconHelper {
    private UiIconHelper() {
    }

    /**
     * Cree une icone Ikonli stylisee pour les boutons.
     * @param iconCode code de l'icone (ex: "mdi2p-plus-circle")
     * @param size taille en pixels
     * @param color couleur CSS (ex: "#F5ECD7")
     */
    public static FontIcon makeIcon(String iconCode, int size, String color) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(size);
        if (color != null && !color.isBlank()) {
            icon.setIconColor(Color.web(color));
        }
        return icon;
    }

    /**
     * Cree un bouton d'operation avec icone + label (sans raccourci clavier).
     */
    public static Button makeOpButton(String iconCode, String label,
                                      String styleClass, double w, double h) {
        String iconColor = opIconColor(styleClass);
        FontIcon icon = makeIcon(iconCode, 22, iconColor);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + iconColor + ";");
        lbl.setWrapText(true);
        lbl.setMaxWidth(w);
        lbl.setAlignment(Pos.CENTER);
        lbl.setTextAlignment(TextAlignment.CENTER);

        VBox content = new VBox(4, icon, lbl);
        content.setAlignment(Pos.CENTER);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setPrefSize(w, h);
        btn.setMinSize(w, h);
        btn.setMaxSize(w, h);
        btn.getStyleClass().addAll("button", styleClass);
        return btn;
    }

    public static FontIcon toastIcon(ToastService.ToastType type, int size) {
        String iconCode = toastIconCode(type);
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(size);
        return icon;
    }

    public static FontIcon statusIcon(String type, int size) {
        String iconCode = statusIconCode(type);
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(size);
        return icon;
    }

    public static String categoryFallbackIcon(String name) {
        if (name == null) {
            return "mdi2s-star-outline";
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("hot beverages")) {
            return "mdi2c-coffee";
        }
        if (normalized.equals("cold beverages")) {
            return "mdi2g-glass-cocktail";
        }
        if (normalized.equals("sweets")) {
            return "mdi2c-cake-variant";
        }
        if (normalized.equals("salties")) {
            return "mdi2f-food";
        }
        if (normalized.equals("cards")) {
            return "mdi2c-credit-card-outline";
        }
        if (normalized.equals("additions")) {
            return "mdi2p-plus-box-outline";
        }
        return "mdi2s-star-outline";
    }

    private static String toastIconCode(ToastService.ToastType type) {
        if (type == null) {
            return "mdi2i-information";
        }
        return switch (type) {
            case SUCCESS -> "mdi2c-check-circle";
            case WARNING -> "mdi2a-alert";
            case DANGER -> "mdi2c-close-circle";
            case INFO -> "mdi2i-information";
        };
    }

    private static String statusIconCode(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success" -> "mdi2c-check-circle";
            case "warning" -> "mdi2a-alert";
            case "error", "danger" -> "mdi2c-close-circle";
            default -> "mdi2i-information";
        };
    }

    private static String opIconColor(String styleClass) {
        String normalized = styleClass == null ? "" : styleClass.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success", "danger", "warning" -> "#FFFFFF";
            case "elevated" -> "#6B2D1A";
            case "accent" -> "#F5ECD7";
            default -> "#F5ECD7";
        };
    }
}
