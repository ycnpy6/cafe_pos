package com.cafepos.controllers;

import com.cafepos.model.Ingredient;
import com.cafepos.model.StockUnit;
import com.cafepos.model.UnitType;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User-facing form to create / edit an {@link Ingredient}. Replaces the
 * old inline-table editing flow (which exposed jargon columns such as
 * "Taille pack" / "Prix pack") with a guided dialog where the user picks:
 *   - product family (liquide / solide / pièce)
 *   - unit (KG/G, L/CL/ML, UNIT/PIECE/PACK)
 *   - pack contents (e.g. 1.3 L for a syrup bottle)
 *   - pack purchase price
 *   - stock either in packs (e.g. 3 bouteilles) or directly in the unit
 *
 * Stock conversion is delegated to {@link StockUnit} / {@link UnitType}
 * exactly like the rest of the system, so no math is re-implemented here.
 * Available units come from {@link com.cafepos.model.UnitRegistry} so
 * admin-defined custom units appear in the form alongside built-ins.
 */
public final class IngredientEditorDialog {

    private IngredientEditorDialog() {}

    /** Friendly families exposed in the form — labels for {@link CustomUnit.Family}. */
    private static final java.util.Map<com.cafepos.model.CustomUnit.Family, String> FAMILY_LABELS;
    static {
        java.util.Map<com.cafepos.model.CustomUnit.Family, String> m = new java.util.EnumMap<>(com.cafepos.model.CustomUnit.Family.class);
        m.put(com.cafepos.model.CustomUnit.Family.LIQUIDE, "Liquide");
        m.put(com.cafepos.model.CustomUnit.Family.SOLIDE,  "Solide");
        m.put(com.cafepos.model.CustomUnit.Family.PIECE,   "Pièce");
        FAMILY_LABELS = m;
    }

    private static com.cafepos.model.CustomUnit.Family familyForUnit(String unit) {
        if (unit == null) return com.cafepos.model.CustomUnit.Family.PIECE;
        com.cafepos.model.UnitRegistry.Entry e = com.cafepos.model.UnitRegistry.resolve(unit);
        return e.family();
    }

    /** Long human label shown next to each unit symbol (built-ins). */
    private static final Map<String, String> UNIT_LABELS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Volume
        m.put("L",     "L      (Litre)");
        m.put("CL",    "CL     (Centilitre)");
        m.put("ML",    "ML     (Millilitre)");
        m.put("FLOZ",  "FL OZ  (Once liquide US ≈ 29.57 ml)");
        m.put("CUP",   "CUP    (Tasse 240 ml)");
        m.put("SCOOP", "SCOOP  (Boule 60 ml)");
        m.put("TBSP",  "TBSP   (Cuillère à soupe ≈ 14.8 ml)");
        m.put("TSP",   "TSP    (Cuillère à café ≈ 4.93 ml)");
        m.put("PINT",  "PINT   (≈ 473 ml)");
        m.put("QUART", "QUART  (≈ 946 ml)");
        m.put("GAL",   "GAL    (Gallon US ≈ 3.785 l)");
        // Mass
        m.put("KG",    "KG     (Kilogramme)");
        m.put("G",     "G      (Gramme)");
        m.put("MG",    "MG     (Milligramme)");
        m.put("LB",    "LB     (Livre ≈ 453.6 g)");
        m.put("OZ",    "OZ     (Once ≈ 28.35 g)");
        // Count
        m.put("UNIT",  "Unité");
        m.put("PIECE", "Pièce");
        m.put("PACK",  "Pack");
        m.put("BOX",   "Boîte");
        m.put("DOZEN", "Douzaine (×12)");
        UNIT_LABELS = m;
    }

    private static String labelFor(String unit) {
        if (unit == null) return "";
        String key = unit.trim().toUpperCase();
        String builtin = UNIT_LABELS.get(key);
        if (builtin != null) return builtin;
        // Custom unit — derive a hint from its registry entry.
        com.cafepos.model.UnitRegistry.Entry e = com.cafepos.model.UnitRegistry.resolve(unit);
        String suffix = e.factorToBase() == 1.0
                ? "(base " + e.baseUnit() + ")"
                : "(× " + trimNumber(e.factorToBase()) + " " + e.baseUnit() + ")";
        if (e.label() != null && !e.label().isBlank()) {
            return key + "  — " + e.label() + " " + suffix;
        }
        return key + "  " + suffix;
    }

    private static String trimNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.4f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Show the dialog and return the populated Ingredient. The returned
     * object carries the id of {@code existing} when editing (so the
     * caller can decide between insert / update).
     */
    public static Optional<Ingredient> showAndWait(Ingredient existing) {
        boolean editing = existing != null && existing.getId() > 0;
        Dialog<Ingredient> dlg = new Dialog<>();
        dlg.setTitle(editing ? "Modifier l'ingrédient" : "Nouvel ingrédient");
        dlg.setHeaderText(editing
                ? "Modifier " + existing.getName()
                : "Décrire le produit acheté (bouteille, sac, paquet…)");

        ButtonType okBtn = new ButtonType(editing ? "Enregistrer" : "Créer", ButtonType.OK.getButtonData());
        dlg.getDialogPane().getButtonTypes().setAll(okBtn, ButtonType.CANCEL);

        // ── widgets ──────────────────────────────────────────────────────
        TextField nameField = new TextField(existing == null ? "" : existing.getName());
        nameField.setPromptText("Ex: Sirop fraise, Café en grains, Lait UHT…");

        ComboBox<com.cafepos.model.CustomUnit.Family> familyCombo = new ComboBox<>();
        familyCombo.getItems().addAll(com.cafepos.model.CustomUnit.Family.values());
        familyCombo.setCellFactory(cb -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.cafepos.model.CustomUnit.Family item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FAMILY_LABELS.getOrDefault(item, item.name()));
            }
        });
        familyCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.cafepos.model.CustomUnit.Family item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FAMILY_LABELS.getOrDefault(item, item.name()));
            }
        });
        com.cafepos.model.CustomUnit.Family initialFamily = existing == null
                ? com.cafepos.model.CustomUnit.Family.LIQUIDE
                : familyForUnit(existing.getUnit());
        familyCombo.getSelectionModel().select(initialFamily);

        ComboBox<String> unitCombo = new ComboBox<>();
        unitCombo.setCellFactory(cb -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
            }
        });
        unitCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
            }
        });

        TextField packSizeField  = new TextField(existing == null ? "" : trim(existing.getPackageSize()));
        packSizeField.setPromptText("Ex: 1.3");
        Label packUnitLabel = new Label();
        packUnitLabel.setStyle("-fx-text-fill: #6B2D1A; -fx-font-weight: bold;");

        TextField packPriceField = new TextField(existing == null ? "" : trim(existing.getPackagePrice()));
        packPriceField.setPromptText("Prix total du pack en DZD");

        TextField stockPacksField = new TextField();
        stockPacksField.setPromptText("Ex: 3 bouteilles achetées");

        TextField stockUnitField  = new TextField(existing == null ? "" : trim(existing.getStockQuantity()));
        stockUnitField.setPromptText("Total dans l'unité");

        TextField minField = new TextField(existing == null ? "" : trim(existing.getMinQuantity()));
        minField.setPromptText("Alerte stock bas");

        CheckBox activeBox = new CheckBox("Ingrédient actif");
        activeBox.setSelected(existing == null || existing.isActive());

        Label previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-text-fill: #3A6A2A; -fx-font-style: italic;");

        // ── helpers ──────────────────────────────────────────────────────
        Runnable refreshUnits = () -> {
            com.cafepos.model.CustomUnit.Family fam = familyCombo.getSelectionModel().getSelectedItem();
            String previouslySelected = unitCombo.getSelectionModel().getSelectedItem();
            java.util.List<String> options = com.cafepos.model.UnitRegistry.displayUnitsForFamily(fam);
            unitCombo.getItems().setAll(options);
            if (previouslySelected != null && options.contains(previouslySelected)) {
                unitCombo.getSelectionModel().select(previouslySelected);
            } else if (existing != null && existing.getUnit() != null
                    && options.contains(existing.getUnit().trim().toUpperCase())) {
                unitCombo.getSelectionModel().select(existing.getUnit().trim().toUpperCase());
            } else if (!options.isEmpty()) {
                unitCombo.getSelectionModel().selectFirst();
            }
        };
        refreshUnits.run();

        Runnable refreshPreview = () -> {
            String unit  = unitCombo.getSelectionModel().getSelectedItem();
            double size  = parse(packSizeField.getText());
            double price = parse(packPriceField.getText());
            double stock = parse(stockUnitField.getText());
            packUnitLabel.setText(unit == null ? "" : unit);
            if (size > 0 && price > 0) {
                double unitCost = price / size;
                previewLabel.setText(String.format(
                        "1 pack = %s %s → coût unitaire ≈ %.2f DZD / %s%s",
                        trim(size), unit, unitCost, unit,
                        stock > 0 ? "  •  Stock total: " + trim(stock) + " " + unit : ""));
            } else {
                previewLabel.setText("");
            }
        };

        // packs ↔ unit synchronisation (one-way each, no echo loop)
        boolean[] syncing = { false };
        stockPacksField.textProperty().addListener((o, ov, nv) -> {
            if (syncing[0]) return;
            double size = parse(packSizeField.getText());
            double packs = parse(nv);
            if (size > 0 && packs >= 0) {
                syncing[0] = true;
                stockUnitField.setText(trim(packs * size));
                syncing[0] = false;
            }
            refreshPreview.run();
        });
        stockUnitField.textProperty().addListener((o, ov, nv) -> {
            if (syncing[0]) return;
            double size = parse(packSizeField.getText());
            double total = parse(nv);
            if (size > 0 && total >= 0) {
                syncing[0] = true;
                stockPacksField.setText(trim(total / size));
                syncing[0] = false;
            }
            refreshPreview.run();
        });
        packSizeField.textProperty().addListener((o, ov, nv) -> {
            // re-derive packs from the (now-known) pack size if user filled unit first
            double size = parse(nv);
            double total = parse(stockUnitField.getText());
            if (size > 0 && total > 0) {
                syncing[0] = true;
                stockPacksField.setText(trim(total / size));
                syncing[0] = false;
            }
            refreshPreview.run();
        });
        packPriceField.textProperty().addListener((o, ov, nv) -> refreshPreview.run());
        unitCombo.valueProperty().addListener((o, ov, nv) -> refreshPreview.run());
        familyCombo.valueProperty().addListener((o, ov, nv) -> {
            refreshUnits.run();
            refreshPreview.run();
        });

        // initial preview pass once values are wired
        if (existing != null && existing.getPackageSize() > 0) {
            stockPacksField.setText(trim(existing.getStockQuantity() / existing.getPackageSize()));
        }
        refreshPreview.run();

        // ── layout ───────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 4, 4, 4));
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHalignment(HPos.RIGHT);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setFillWidth(true);
        c1.setPrefWidth(260);
        grid.getColumnConstraints().addAll(c0, c1);

        int r = 0;
        grid.add(new Label("Nom"),                 0, r); grid.add(nameField,      1, r++);
        grid.add(new Label("Type"),                0, r); grid.add(familyCombo,    1, r++);
        grid.add(new Label("Unité"),               0, r); grid.add(unitCombo,      1, r++);

        GridPane packGrid = new GridPane();
        packGrid.setHgap(6);
        packGrid.add(packSizeField, 0, 0);
        packGrid.add(packUnitLabel, 1, 0);
        ColumnConstraints pc0 = new ColumnConstraints(); pc0.setHgrow(Priority.ALWAYS);
        packGrid.getColumnConstraints().addAll(pc0, new ColumnConstraints());
        Label packSizeHint = new Label("Contenance d'un pack");
        packSizeHint.setTooltip(new Tooltip("Quantité contenue dans un pack/bouteille/sac (ex: 1.3 L pour une bouteille de sirop)."));
        grid.add(packSizeHint, 0, r); grid.add(packGrid, 1, r++);

        Label priceHint = new Label("Prix d'achat (pack)");
        priceHint.setTooltip(new Tooltip("Prix total payé pour UN pack — pas par litre/kg."));
        grid.add(priceHint, 0, r); grid.add(packPriceField, 1, r++);

        Label packsHint = new Label("Stock en packs");
        packsHint.setTooltip(new Tooltip("Nombre de bouteilles/sacs/paquets en stock. Calcule automatiquement le total."));
        grid.add(packsHint, 0, r); grid.add(stockPacksField, 1, r++);

        Label unitHint = new Label("Stock total");
        unitHint.setTooltip(new Tooltip("Quantité totale en stock dans l'unité choisie. Recalculé depuis le nombre de packs."));
        grid.add(unitHint, 0, r); grid.add(stockUnitField, 1, r++);

        grid.add(new Label("Seuil d'alerte"),      0, r); grid.add(minField,       1, r++);
        grid.add(new Label(""),                    0, r); grid.add(activeBox,      1, r++);
        grid.add(previewLabel, 0, r, 2, 1);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().setPrefWidth(480);

        // ── validation & result ──────────────────────────────────────────
        Node okNode = dlg.getDialogPane().lookupButton(okBtn);
        okNode.disableProperty().bind(
                nameField.textProperty().isEmpty()
                        .or(packSizeField.textProperty().isEmpty())
        );

        dlg.setResultConverter(btn -> {
            if (btn != okBtn) return null;
            String name = nameField.getText().trim();
            String unit = unitCombo.getSelectionModel().getSelectedItem();
            if (unit == null || unit.isBlank()) unit = "UNIT";
            double packSize  = Math.max(0, parse(packSizeField.getText()));
            double packPrice = Math.max(0, parse(packPriceField.getText()));
            double stockQty  = Math.max(0, parse(stockUnitField.getText()));
            double minQty    = Math.max(0, parse(minField.getText()));
            int id = editing ? existing.getId() : 0;
            return new Ingredient(id, name, unit, packSize, packPrice, stockQty, minQty, activeBox.isSelected());
        });

        Platform.runLater(nameField::requestFocus);
        return dlg.showAndWait();
    }

    private static double parse(String s) {
        if (s == null) return 0;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return 0;
        try { return Double.parseDouble(t); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.format(java.util.Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
