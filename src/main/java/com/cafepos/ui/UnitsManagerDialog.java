package com.cafepos.ui;

import com.cafepos.dao.CustomUnitDAO;
import com.cafepos.model.CustomUnit;
import com.cafepos.model.UnitRegistry;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Manager screen for the {@code custom_units} table. Lets the operator:
 *   <ul>
 *     <li>see every unit known to the system (built-in + custom)</li>
 *     <li>add a brand new unit (e.g. "TASSE_FR" = 250 ml)</li>
 *     <li>edit / disable / delete a custom unit</li>
 *     <li>override a built-in unit's factor by creating a custom row that
 *         shadows the same display name</li>
 *   </ul>
 *
 * <p>Existing ingredients are not retro-converted: each one snapshots its
 * unit factor at insert/update time.
 */
public final class UnitsManagerDialog {
    private static final Logger LOG = LoggerFactory.getLogger(UnitsManagerDialog.class);
    private static final CustomUnitDAO DAO = new CustomUnitDAO();

    private UnitsManagerDialog() {}

    /** Open the manager. Returns {@code true} if any unit was added or modified. */
    public static boolean showAndWait(Window owner) {
        Dialog<ButtonType> dlg = new Dialog<>();
        if (owner != null) dlg.initOwner(owner);
        dlg.setTitle("Gestion des unités de mesure");
        dlg.setHeaderText("Unités intégrées + unités personnalisées. Vous pouvez créer des "
                + "unités locales (ex: TASSE_FR = 250 ml) ou remplacer une unité intégrée.");
        dlg.setResizable(true);

        ButtonType closeBtn = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(closeBtn);

        TableView<UnitRow> table = new TableView<>();
        table.setPrefSize(720, 380);

        TableColumn<UnitRow, String> displayCol = new TableColumn<>("Symbole");
        displayCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().displayUnit));
        displayCol.setPrefWidth(120);

        TableColumn<UnitRow, String> labelCol = new TableColumn<>("Libellé");
        labelCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().label));
        labelCol.setPrefWidth(180);

        TableColumn<UnitRow, String> famCol = new TableColumn<>("Famille");
        famCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().family.name()));
        famCol.setPrefWidth(90);

        TableColumn<UnitRow, String> baseCol = new TableColumn<>("Base");
        baseCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().baseUnit));
        baseCol.setPrefWidth(70);

        TableColumn<UnitRow, String> factorCol = new TableColumn<>("Facteur (→ base)");
        factorCol.setCellValueFactory(c -> new SimpleStringProperty(formatFactor(c.getValue().factorToBase)));
        factorCol.setPrefWidth(130);

        TableColumn<UnitRow, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().custom ? "Personnalisée" : "Intégrée"));
        srcCol.setPrefWidth(110);

        table.getColumns().addAll(List.of(displayCol, labelCol, famCol, baseCol, factorCol, srcCol));

        Button addBtn    = new Button("Nouvelle unité");
        Button editBtn   = new Button("Modifier / Remplacer");
        Button deleteBtn = new Button("Supprimer");
        addBtn.getStyleClass().add("primary-button");
        editBtn.getStyleClass().add("ghost-button");
        deleteBtn.getStyleClass().add("ghost-button");

        boolean[] dirty = { false };

        Runnable reload = () -> {
            UnitRegistry.refresh();
            ObservableList<UnitRow> rows = FXCollections.observableArrayList();
            List<CustomUnit> customs;
            try {
                customs = DAO.findAll();
            } catch (Exception ex) {
                LOG.error("Lecture custom_units échouée", ex);
                customs = new ArrayList<>();
                showError(owner, "Lecture impossible: " + ex.getMessage());
            }
            for (CustomUnit cu : customs) {
                rows.add(UnitRow.fromCustom(cu));
            }
            for (UnitRegistry.Entry e : UnitRegistry.all()) {
                if (e.custom()) continue; // already covered above
                rows.add(UnitRow.fromBuiltin(e));
            }
            table.setItems(rows);
        };
        reload.run();

        editBtn.setDisable(true);
        deleteBtn.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            editBtn.setDisable(nv == null);
            deleteBtn.setDisable(nv == null || nv.customId <= 0);
        });

        addBtn.setOnAction(ev -> {
            Optional<CustomUnit> opt = UnitFormDialog.create(owner, null);
            if (opt.isPresent()) {
                try {
                    DAO.insert(opt.get());
                    dirty[0] = true;
                    reload.run();
                } catch (Exception ex) {
                    LOG.error("Insert custom_unit échoué", ex);
                    showError(owner, "Création impossible: " + ex.getMessage());
                }
            }
        });

        editBtn.setOnAction(ev -> {
            UnitRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) return;
            CustomUnit seed = row.customId > 0
                    ? new CustomUnit(row.customId, row.displayUnit, row.baseUnit,
                            row.factorToBase, row.family, row.label, row.active)
                    // Built-in row → pre-fill a NEW custom override.
                    : new CustomUnit(0, row.displayUnit, row.baseUnit,
                            row.factorToBase, row.family, row.label, true);
            boolean overridingBuiltin = row.customId <= 0;
            Optional<CustomUnit> opt = UnitFormDialog.create(owner, seed, overridingBuiltin);
            if (opt.isPresent()) {
                try {
                    if (opt.get().getId() > 0) {
                        DAO.update(opt.get());
                    } else {
                        DAO.insert(opt.get());
                    }
                    dirty[0] = true;
                    reload.run();
                } catch (Exception ex) {
                    LOG.error("Update custom_unit échoué", ex);
                    showError(owner, "Modification impossible: " + ex.getMessage());
                }
            }
        });

        deleteBtn.setOnAction(ev -> {
            UnitRow row = table.getSelectionModel().getSelectedItem();
            if (row == null || row.customId <= 0) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Supprimer l'unité personnalisée \"" + row.displayUnit + "\" ?\n"
                            + "Les ingrédients existants gardent leur facteur (snapshot) "
                            + "mais ne pourront plus utiliser ce symbole pour de nouvelles saisies.",
                    ButtonType.OK, ButtonType.CANCEL);
            if (owner != null) confirm.initOwner(owner);
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try {
                    DAO.delete(row.customId);
                    dirty[0] = true;
                    reload.run();
                } catch (Exception ex) {
                    LOG.error("Delete custom_unit échoué", ex);
                    showError(owner, "Suppression impossible: " + ex.getMessage());
                }
            }
        });

        HBox toolbar = new HBox(8, addBtn, editBtn, deleteBtn);
        VBox box = new VBox(10, toolbar, table);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();
        UnitRegistry.refresh();
        return dirty[0];
    }

    private static void showError(Window owner, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }

    private static String formatFactor(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Flat view-model row for the table. */
    private static final class UnitRow {
        final int customId; // 0 when built-in
        final String displayUnit;
        final String baseUnit;
        final double factorToBase;
        final CustomUnit.Family family;
        final String label;
        final boolean custom;
        final boolean active;

        private UnitRow(int customId, String displayUnit, String baseUnit, double factorToBase,
                        CustomUnit.Family family, String label, boolean custom, boolean active) {
            this.customId = customId;
            this.displayUnit = displayUnit;
            this.baseUnit = baseUnit;
            this.factorToBase = factorToBase;
            this.family = family;
            this.label = label == null ? "" : label;
            this.custom = custom;
            this.active = active;
        }

        static UnitRow fromCustom(CustomUnit cu) {
            return new UnitRow(cu.getId(), cu.getDisplayUnit(), cu.getBaseUnit(),
                    cu.getFactorToBase(), cu.getFamily(), cu.getLabel(), true, cu.isActive());
        }

        static UnitRow fromBuiltin(UnitRegistry.Entry e) {
            return new UnitRow(0, e.displayUnit(), e.baseUnit(), e.factorToBase(),
                    e.family(), e.label(), false, true);
        }
    }

    /** Inner form dialog used to create / edit a single CustomUnit. */
    private static final class UnitFormDialog {
        private UnitFormDialog() {}

        static Optional<CustomUnit> create(Window owner, CustomUnit existing) {
            return create(owner, existing, false);
        }

        static Optional<CustomUnit> create(Window owner, CustomUnit existing, boolean overridingBuiltin) {
            Dialog<CustomUnit> dlg = new Dialog<>();
            if (owner != null) dlg.initOwner(owner);
            dlg.setTitle(existing == null ? "Nouvelle unité"
                    : (existing.getId() > 0 ? "Modifier l'unité" : "Remplacer l'unité intégrée"));
            if (overridingBuiltin) {
                dlg.setHeaderText("Création d'une surcharge — n'affecte que les nouveaux "
                        + "ingrédients/recettes. Les enregistrements existants conservent "
                        + "leur facteur d'origine.");
            }

            ButtonType okBtn = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
            dlg.getDialogPane().getButtonTypes().setAll(okBtn, ButtonType.CANCEL);

            TextField symbolField = new TextField(existing == null ? "" : existing.getDisplayUnit());
            symbolField.setPromptText("Ex: TASSE_FR, BIDON, …");
            symbolField.setDisable(overridingBuiltin); // can't rename a built-in override

            TextField labelField = new TextField(existing == null || existing.getLabel() == null
                    ? "" : existing.getLabel());
            labelField.setPromptText("Description courte (optionnel)");

            ComboBox<CustomUnit.Family> familyCombo = new ComboBox<>();
            familyCombo.getItems().addAll(CustomUnit.Family.values());
            familyCombo.getSelectionModel().select(existing == null
                    ? CustomUnit.Family.LIQUIDE : existing.getFamily());
            familyCombo.setDisable(overridingBuiltin);

            ComboBox<String> baseCombo = new ComboBox<>();
            Runnable refreshBases = () -> {
                CustomUnit.Family f = familyCombo.getSelectionModel().getSelectedItem();
                baseCombo.getItems().setAll(basesFor(f));
                if (existing != null && baseCombo.getItems().contains(existing.getBaseUnit())) {
                    baseCombo.getSelectionModel().select(existing.getBaseUnit());
                } else {
                    baseCombo.getSelectionModel().selectFirst();
                }
            };
            refreshBases.run();
            familyCombo.valueProperty().addListener((o, ov, nv) -> refreshBases.run());
            baseCombo.setDisable(overridingBuiltin);

            TextField factorField = new TextField(existing == null ? "1"
                    : formatFactor(existing.getFactorToBase()));
            factorField.setPromptText("Combien d'unités de base = 1 de cette unité ?");

            CheckBox activeBox = new CheckBox("Active");
            activeBox.setSelected(existing == null || existing.isActive());

            Label hint = new Label("Ex: 1 TASSE_FR = 250 ML → famille Liquide, base ML, facteur 250.");
            hint.setWrapText(true);
            hint.setStyle("-fx-text-fill: #3A6A2A; -fx-font-style: italic;");

            GridPane g = new GridPane();
            g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
            int r = 0;
            g.add(new Label("Symbole"),  0, r); g.add(symbolField,  1, r++);
            g.add(new Label("Libellé"),  0, r); g.add(labelField,   1, r++);
            g.add(new Label("Famille"),  0, r); g.add(familyCombo,  1, r++);
            g.add(new Label("Base"),     0, r); g.add(baseCombo,    1, r++);
            g.add(new Label("Facteur"),  0, r); g.add(factorField,  1, r++);
            g.add(activeBox,             1, r++);
            g.add(hint,                  0, r, 2, 1);

            dlg.getDialogPane().setContent(g);

            Node okNode = dlg.getDialogPane().lookupButton(okBtn);
            Runnable validate = () -> {
                boolean bad = symbolField.getText().trim().isEmpty()
                        || baseCombo.getSelectionModel().getSelectedItem() == null
                        || parseDouble(factorField.getText()) <= 0;
                okNode.setDisable(bad);
            };
            validate.run();
            symbolField.textProperty().addListener((o, ov, nv) -> validate.run());
            factorField.textProperty().addListener((o, ov, nv) -> validate.run());
            baseCombo.valueProperty().addListener((o, ov, nv) -> validate.run());

            Platform.runLater(symbolField::requestFocus);

            dlg.setResultConverter(btn -> {
                if (btn != okBtn) return null;
                String label = labelField.getText() == null ? null : labelField.getText().trim();
                if (label != null && label.isEmpty()) label = null;
                int id = existing != null && existing.getId() > 0 ? existing.getId() : 0;
                return new CustomUnit(
                        id,
                        symbolField.getText(),
                        baseCombo.getSelectionModel().getSelectedItem(),
                        parseDouble(factorField.getText()),
                        familyCombo.getSelectionModel().getSelectedItem(),
                        label,
                        activeBox.isSelected()
                );
            });
            return dlg.showAndWait();
        }

        private static List<String> basesFor(CustomUnit.Family family) {
            if (family == null) return List.of("UNIT");
            return switch (family) {
                case LIQUIDE -> List.of("ML");
                case SOLIDE  -> List.of("G");
                case PIECE   -> List.of("UNIT");
            };
        }

        private static double parseDouble(String s) {
            if (s == null) return 0;
            try { return Double.parseDouble(s.trim().replace(',', '.')); }
            catch (Exception ex) { return 0; }
        }
    }
}
