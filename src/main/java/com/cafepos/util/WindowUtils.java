package com.cafepos.util;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class WindowUtils {
    private WindowUtils() {
    }

    public static void applyFullSize(Stage stage) {
        if (stage == null) {
            return;
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setMaximized(true);
    }
}
