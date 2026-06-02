package com.cafepos.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public final class ToastService {
    public enum ToastType {
        INFO,
        SUCCESS,
        WARNING,
        DANGER
    }

    private static final ToastService INSTANCE = new ToastService();
    private static final int MAX_VISIBLE = 2;

    private StackPane root;
    private HBox statusBar;
    private VBox container;

    private ToastService() {
    }

    public static void install(StackPane root, HBox statusBar) {
        Platform.runLater(() -> INSTANCE.installInternal(root, statusBar));
    }

    public static void show(String message, ToastType type) {
        Platform.runLater(() -> INSTANCE.showInternal(message, type));
    }

    private void installInternal(StackPane rootPane, HBox statusRow) {
        if (rootPane == null) {
            return;
        }
        this.root = rootPane;
        this.statusBar = statusRow;

        if (container == null) {
            container = new VBox(6);
            container.setAlignment(Pos.BOTTOM_RIGHT);
            container.setMouseTransparent(true);
            StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
            rootPane.getChildren().add(container);
        } else if (!rootPane.getChildren().contains(container)) {
            rootPane.getChildren().add(container);
        }

        updateContainerMargin();
        if (statusBar != null) {
            statusBar.heightProperty().addListener((obs, oldVal, newVal) -> updateContainerMargin());
        }
    }

    private void updateContainerMargin() {
        if (container == null) {
            return;
        }
        double statusHeight = statusBar == null ? 0 : Math.max(0, statusBar.getHeight());
        StackPane.setMargin(container, new Insets(0, 4, statusHeight + 4, 0));
    }

    private void showInternal(String message, ToastType type) {
        if (root == null || container == null) {
            return;
        }
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) {
            return;
        }

        HBox toast = buildToast(safeMessage, type == null ? ToastType.INFO : type);
        container.getChildren().add(toast);
        while (container.getChildren().size() > MAX_VISIBLE) {
            container.getChildren().remove(0);
        }

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(120), toast);
        slideIn.setFromX(20);
        slideIn.setToX(0);
        slideIn.play();

        PauseTransition wait = new PauseTransition(Duration.millis(2500));
        wait.setOnFinished(evt -> fadeOut(toast));
        wait.play();
    }

    private HBox buildToast(String message, ToastType type) {
        HBox toast = new HBox(8);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMouseTransparent(true);
        toast.setMinWidth(240);
        toast.setPrefWidth(240);
        toast.setMaxWidth(240);
        toast.setMinHeight(36);
        toast.setMaxHeight(56);
        toast.setStyle("-fx-padding: 8px 12px;"
                + "-fx-background-color: -color-bg-inset;"
                + "-fx-border-color: -color-border-default;"
                + "-fx-border-width: 1px;"
                + "-fx-border-radius: 6px;"
                + "-fx-background-radius: 6px;");

        Region marker = new Region();
        marker.setMinWidth(3);
        marker.setPrefWidth(3);
        marker.setMaxWidth(3);
        marker.setMinHeight(24);
        marker.setStyle("-fx-background-color: " + colorFor(type) + "; -fx-background-radius: 2px;");

        FontIcon icon = UiIconHelper.toastIcon(type, 18);
        icon.setStyle("-fx-icon-color: " + colorFor(type) + ";");

        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(200);
        text.setStyle("-fx-font-size: 12px;");
        HBox.setHgrow(text, Priority.ALWAYS);

        toast.getChildren().addAll(marker, icon, text);
        return toast;
    }

    private void fadeOut(HBox toast) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), toast);
        fade.setToValue(0);
        fade.setOnFinished(evt -> {
            if (container != null) {
                container.getChildren().remove(toast);
            }
        });
        fade.play();
    }

    private String colorFor(ToastType type) {
        return switch (type) {
            case SUCCESS -> "-color-success-emphasis";
            case WARNING -> "-color-warning-emphasis";
            case DANGER -> "-color-danger-emphasis";
            case INFO -> "-color-accent-emphasis";
        };
    }
}
