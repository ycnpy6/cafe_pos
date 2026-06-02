package com.cafepos.ui;

import com.cafepos.MainApp;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public abstract class BaseDialog extends Stage {
    private final Stage owner;
    private final double dialogWidth;
    private final double dialogHeight;

    protected BaseDialog(Stage owner, double width, double height) {
        this.owner = owner;
        this.dialogWidth = width;
        this.dialogHeight = height;

        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            initOwner(owner);
        }
        MainApp.applyAppIcon(this);
        setWidth(width);
        setHeight(height);
    }

    protected final void initializeDialog() {
        VBox root = buildContent();
        if (root == null) {
            throw new IllegalStateException("Dialog content cannot be null");
        }
        root.setStyle("""
                -fx-background-color: #F5ECD7;
                -fx-border-color: #D4B896;
                -fx-border-width: 1px;
                -fx-border-radius: 10px;
                -fx-background-radius: 10px;
                -fx-padding: 20px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 20, 0, 0, 4);
                """);

        Scene scene = new Scene(root, dialogWidth, dialogHeight);
        scene.setFill(Color.TRANSPARENT);
        MainApp.applyBrandTheme(scene);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        setScene(scene);

        setOnShown(e -> centerOnOwner());
    }

    private void centerOnOwner() {
        if (owner == null) {
            return;
        }
        double x = owner.getX() + (owner.getWidth() - dialogWidth) / 2.0;
        double y = owner.getY() + (owner.getHeight() - dialogHeight) / 2.0;
        setX(Math.max(0, x));
        setY(Math.max(0, y));
    }

    protected abstract VBox buildContent();
}
