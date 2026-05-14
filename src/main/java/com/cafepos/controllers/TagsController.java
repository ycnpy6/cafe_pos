package com.cafepos.controllers;

import com.cafepos.dao.TagDAO;
import com.cafepos.dao.TagGroupDAO;
import com.cafepos.model.Tag;
import com.cafepos.model.TagGroup;
import com.cafepos.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TagsController {
    private static final Logger LOG = LoggerFactory.getLogger(TagsController.class);
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final TagDAO tagDAO = new TagDAO();

    @FXML
    private TableView<TagGroup> groupTable;
    @FXML
    private TableColumn<TagGroup, String> groupNameColumn;
    @FXML
    private TableColumn<TagGroup, String> groupMultiColumn;
    @FXML
    private TextField groupNameField;
    @FXML
    private CheckBox multiSelectBox;

    @FXML
    private TableView<Tag> tagTable;
    @FXML
    private TableColumn<Tag, String> tagNameColumn;
    @FXML
    private TableColumn<Tag, String> tagPriceColumn;
    @FXML
    private TextField tagNameField;
    @FXML
    private TextField tagPriceField;

    @FXML
    private void initialize() {
        configureTables();
        loadGroups();
        groupTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadTags(newVal.getId());
            }
        });
    }

    private void configureTables() {
        groupNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        groupMultiColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isMultiSelect() ? "Oui" : "Non"));

        tagNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        tagPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().getPriceModifier())));
    }

    private void loadGroups() {
        Task<List<TagGroup>> task = new Task<>() {
            @Override
            protected List<TagGroup> call() throws Exception {
                return tagGroupDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> groupTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> LOG.error("Erreur groupes", task.getException()));
        Thread thread = new Thread(task, "group-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadTags(int groupId) {
        Task<List<Tag>> task = new Task<>() {
            @Override
            protected List<Tag> call() throws Exception {
                return tagDAO.findByGroupId(groupId);
            }
        };
        task.setOnSucceeded(evt -> tagTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> LOG.error("Erreur tags", task.getException()));
        Thread thread = new Thread(task, "tag-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onAddGroup() {
        String name = groupNameField.getText();
        if (name == null || name.isBlank()) {
            showAlert("Nom requis", "Saisissez un nom de groupe.");
            return;
        }
        boolean multi = multiSelectBox.isSelected();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                tagGroupDAO.insertGroup(name.trim(), multi);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            groupNameField.clear();
            multiSelectBox.setSelected(false);
            loadGroups();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout groupe", task.getException());
            showAlert("Erreur", "Ajout groupe impossible.");
        });
        Thread thread = new Thread(task, "group-add");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onAddTag() {
        TagGroup group = groupTable.getSelectionModel().getSelectedItem();
        if (group == null) {
            showAlert("Groupe requis", "Selectionnez un groupe.");
            return;
        }
        String name = tagNameField.getText();
        if (name == null || name.isBlank()) {
            showAlert("Nom requis", "Saisissez un nom d'option.");
            return;
        }
        double price = parseDouble(tagPriceField.getText());
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                tagDAO.insertTag(group.getId(), name.trim(), price);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            tagNameField.clear();
            tagPriceField.clear();
            loadTags(group.getId());
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout tag", task.getException());
            showAlert("Erreur", "Ajout option impossible.");
        });
        Thread thread = new Thread(task, "tag-add");
        thread.setDaemon(true);
        thread.start();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
