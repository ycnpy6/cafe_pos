package com.cafepos.ui;

import com.cafepos.model.Tag;
import com.cafepos.model.TagGroup;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TagSelectionDialog {
    private TagSelectionDialog() {
    }

    public static List<Tag> show(Window owner, String productName, List<TagGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptyList();
        }

        Dialog<List<Tag>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Options");
        dialog.setHeaderText(productName);

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));

        Map<Tag, Node> controls = new HashMap<>();

        for (TagGroup group : groups) {
            Label groupLabel = new Label(group.getName());
            groupLabel.getStyleClass().add("subtitle");
            root.getChildren().add(groupLabel);

            if (group.isMultiSelect()) {
                for (Tag tag : group.getTags()) {
                    CheckBox check = new CheckBox(formatTag(tag));
                    check.setMinHeight(44);
                    controls.put(tag, check);
                    root.getChildren().add(check);
                }
            } else {
                ToggleGroup toggleGroup = new ToggleGroup();
                for (Tag tag : group.getTags()) {
                    RadioButton radio = new RadioButton(formatTag(tag));
                    radio.setMinHeight(44);
                    radio.setToggleGroup(toggleGroup);
                    controls.put(tag, radio);
                    root.getChildren().add(radio);
                }
            }
        }

        ButtonType confirm = new ButtonType("Ajouter a la commande");
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(root);

        Node confirmButton = dialog.getDialogPane().lookupButton(confirm);
        if (confirmButton != null) {
            confirmButton.getStyleClass().add("action-button");
            confirmButton.setDisable(false);
        }

        dialog.setResultConverter(type -> {
            if (type != confirm) {
                return null;
            }
            List<Tag> selected = new ArrayList<>();
            for (Map.Entry<Tag, Node> entry : controls.entrySet()) {
                Node node = entry.getValue();
                if (node instanceof CheckBox) {
                    if (((CheckBox) node).isSelected()) {
                        selected.add(entry.getKey());
                    }
                } else if (node instanceof RadioButton) {
                    if (((RadioButton) node).isSelected()) {
                        selected.add(entry.getKey());
                    }
                }
            }
            return selected;
        });

        return dialog.showAndWait().orElse(Collections.emptyList());
    }

    private static String formatTag(Tag tag) {
        double mod = tag.getPriceModifier();
        if (mod == 0) {
            return tag.getName();
        }
        String sign = mod > 0 ? "+" : "";
        return tag.getName() + " (" + sign + String.format("%.0f", mod) + " DZD)";
    }
}
