package com.cafepos.controllers;



import com.cafepos.dao.CategoryDAO;
import com.cafepos.dao.CashMovementDAO;
import com.cafepos.dao.IngredientDAO;
import com.cafepos.dao.IngredientMovementDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.StockMovementDAO;
import com.cafepos.dao.TagDAO;
import com.cafepos.dao.TagGroupDAO;
import com.cafepos.model.Category;
import com.cafepos.model.Ingredient;
import com.cafepos.model.Product;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.Tag;
import com.cafepos.model.TagGroup;
import com.cafepos.model.User;
import com.cafepos.service.SessionManager;
import com.cafepos.util.FormatUtils;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StockController {
    private static final Logger LOG = LoggerFactory.getLogger(StockController.class);
    private static final int MAX_TOASTS = 3;
    private static final int DEFAULT_LOW_STOCK = 5;
    private static final String STOCK_THRESHOLD_KEY = "stock.low.threshold";

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();
    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final ProductIngredientDAO productIngredientDAO = new ProductIngredientDAO();
    private final IngredientMovementDAO ingredientMovementDAO = new IngredientMovementDAO();
    private final CashMovementDAO cashMovementDAO = new CashMovementDAO();

    private final ObservableList<ProductRow> masterProducts = FXCollections.observableArrayList();
    private final FilteredList<ProductRow> filteredProducts = new FilteredList<>(masterProducts, row -> true);
    private final ObservableList<IngredientRow> ingredientRows = FXCollections.observableArrayList();
    private final ObservableList<Product> recipeProducts = FXCollections.observableArrayList();
    private final ObservableList<RecipeRow> recipeRows = FXCollections.observableArrayList();

    private final Map<Integer, Category> categoriesById = new HashMap<>();
    private final Map<Integer, String> categoryColors = new HashMap<>();
    private final Map<Integer, IngredientRow> ingredientById = new HashMap<>();

    private int lowStockThreshold = DEFAULT_LOW_STOCK;

    @FXML
    private StackPane rootStack;
    @FXML
    private TextField searchField;
    @FXML
    private Button newProductButton;

    @FXML
    private TableView<ProductRow> productsTable;
    @FXML
    private TableColumn<ProductRow, Boolean> activeColumn;
    @FXML
    private TableColumn<ProductRow, String> nameColumn;
    @FXML
    private TableColumn<ProductRow, Category> categoryColumn;
    @FXML
    private TableColumn<ProductRow, Boolean> preparedColumn;
    @FXML
    private TableColumn<ProductRow, Double> priceColumn;
    @FXML
    private TableColumn<ProductRow, Double> costColumn;
    @FXML
    private TableColumn<ProductRow, Integer> stockColumn;

    @FXML
    private Button newIngredientButton;
    @FXML
    private Button purchaseIngredientButton;
    @FXML
    private TableView<IngredientRow> ingredientsTable;
    @FXML
    private TableColumn<IngredientRow, Boolean> ingredientActiveColumn;
    @FXML
    private TableColumn<IngredientRow, String> ingredientNameColumn;
    @FXML
    private TableColumn<IngredientRow, String> ingredientUnitColumn;
    @FXML
    private TableColumn<IngredientRow, Double> ingredientStockColumn;
    @FXML
    private TableColumn<IngredientRow, Double> ingredientMinColumn;
    @FXML
    private TableColumn<IngredientRow, Double> ingredientPackageSizeColumn;
    @FXML
    private TableColumn<IngredientRow, Double> ingredientPackagePriceColumn;
    @FXML
    private TableColumn<IngredientRow, Double> ingredientUnitCostColumn;

    @FXML
    private ComboBox<Product> recipeProductCombo;
    @FXML
    private ComboBox<IngredientRow> recipeIngredientCombo;
    @FXML
    private TextField recipeQuantityField;
    @FXML
    private Label recipeEstimatedCostLabel;
    @FXML
    private TableView<RecipeRow> recipeTable;
    @FXML
    private TableColumn<RecipeRow, String> recipeIngredientColumn;
    @FXML
    private TableColumn<RecipeRow, String> recipeUnitColumn;
    @FXML
    private TableColumn<RecipeRow, Double> recipeQuantityColumn;
    @FXML
    private TableColumn<RecipeRow, Double> recipeUnitCostColumn;
    @FXML
    private TableColumn<RecipeRow, Double> recipeLineCostColumn;

    @FXML
    private VBox supplementsBox;
    @FXML
    private VBox categoriesBox;
    @FXML
    private VBox toastContainer;

    @FXML
    private void initialize() {
        configureTable();
        configureIngredientsTable();
        configureRecipeTable();
        configureRecipeSelectors();
        configureSearch();
        loadThreshold();
        loadCategories();
        loadIngredients();
        loadSupplements();

        if (newProductButton != null) {
            newProductButton.setDefaultButton(false);
        }

        if (rootStack != null) {
            rootStack.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.F7) {
                    if (searchField != null) {
                        searchField.requestFocus();
                    }
                    event.consume();
                } else if (event.getCode() == KeyCode.N && event.isControlDown()) {
                    onNewProduct();
                    event.consume();
                }
            });
        }
    }

    private void configureTable() {
        productsTable.setItems(filteredProducts);
        productsTable.setEditable(true);

        activeColumn.setCellValueFactory(data -> data.getValue().activeProperty());
        activeColumn.setCellFactory(col -> new ActiveCell());

        nameColumn.setCellValueFactory(data -> data.getValue().nameProperty());
        nameColumn.setCellFactory(col -> new AutoCommitCell<>(new StringConverter<>() {
            @Override
            public String toString(String object) {
                return object == null ? "" : object;
            }

            @Override
            public String fromString(String string) {
                return string == null ? "" : string;
            }
        }));
        nameColumn.setOnEditCommit(evt -> {
            ProductRow row = evt.getRowValue();
            String value = safeString(evt.getNewValue());
            if (value.isBlank()) {
                showToast("warning", "Nom requis");
                productsTable.refresh();
                return;
            }
            row.setName(value);
            if (row.isNew()) {
                maybeCreateProduct(row);
            } else {
                updateProductName(row);
            }
        });

        categoryColumn.setCellValueFactory(data -> data.getValue().categoryProperty());
        categoryColumn.setCellFactory(col -> new CategoryCell());
        categoryColumn.setOnEditCommit(evt -> {
            ProductRow row = evt.getRowValue();
            Category category = evt.getNewValue();
            if (category == null) {
                return;
            }
            row.setCategory(category);
            if (row.isNew()) {
                maybeCreateProduct(row);
            } else {
                updateProductCategory(row, category.getId());
            }
        });

        preparedColumn.setCellValueFactory(data -> data.getValue().preparedProperty());
        preparedColumn.setCellFactory(col -> new PreparedCell());

        priceColumn.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        priceColumn.setCellFactory(col -> new AutoCommitCell<>(new DoubleConverter()));
        priceColumn.setOnEditCommit(evt -> {
            ProductRow row = evt.getRowValue();
            Double newValue = evt.getNewValue();
            double value = newValue == null ? 0 : newValue;
            row.setPrice(value);
            if (row.isNew()) {
                maybeCreateProduct(row);
            } else {
                updateProductPrice(row);
            }
        });

        costColumn.setCellValueFactory(data -> data.getValue().costProperty().asObject());
        costColumn.setCellFactory(col -> new AutoCommitCell<>(new DoubleConverter()));
        costColumn.setOnEditCommit(evt -> {
            ProductRow row = evt.getRowValue();
            Double newValue = evt.getNewValue();
            double value = newValue == null ? 0 : newValue;
            row.setCost(value);
            if (row.isNew()) {
                maybeCreateProduct(row);
            } else {
                updateProductCost(row);
            }
        });

        stockColumn.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        stockColumn.setCellFactory(col -> new StockCell());

        productsTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(ProductRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("row-stock-low", "row-stock-zero", "row-inactive");
                if (empty || row == null) {
                    return;
                }
                if (!row.isActive()) {
                    getStyleClass().add("row-inactive");
                } else if (row.getStock() <= 0) {
                    getStyleClass().add("row-stock-zero");
                } else if (row.getStock() <= lowStockThreshold) {
                    getStyleClass().add("row-stock-low");
                }
            }
        });
    }

    private void configureIngredientsTable() {
        if (ingredientsTable == null) {
            return;
        }
        ingredientsTable.setItems(ingredientRows);
        ingredientsTable.setEditable(true);

        ingredientActiveColumn.setCellValueFactory(data -> data.getValue().activeProperty());
        ingredientActiveColumn.setCellFactory(col -> new IngredientActiveCell());

        ingredientNameColumn.setCellValueFactory(data -> data.getValue().nameProperty());
        ingredientNameColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new StringConverter<>() {
            @Override
            public String toString(String object) {
                return object == null ? "" : object;
            }

            @Override
            public String fromString(String string) {
                return string == null ? "" : string;
            }
        }));
        ingredientNameColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            row.setName(safeString(evt.getNewValue()));
            persistIngredient(row);
        });

        ingredientUnitColumn.setCellValueFactory(data -> data.getValue().unitProperty());
        ingredientUnitColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new StringConverter<>() {
            @Override
            public String toString(String object) {
                return object == null ? "" : object;
            }

            @Override
            public String fromString(String string) {
                return normalizeUnit(string);
            }
        }));
        ingredientUnitColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            row.setUnit(normalizeUnit(evt.getNewValue()));
            persistIngredient(row);
        });

        ingredientStockColumn.setCellValueFactory(data -> data.getValue().stockQuantityProperty().asObject());
        ingredientStockColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new DoubleConverter()));
        ingredientStockColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            if (row == null) {
                return;
            }
            double newQuantity = Math.max(0, safeDouble(evt.getNewValue()));
            if (row.isNew()) {
                row.setStockQuantity(newQuantity);
                persistIngredient(row);
            } else {
                overrideIngredientStock(row, newQuantity);
            }
            refreshRecipeCost();
        });

        ingredientMinColumn.setCellValueFactory(data -> data.getValue().minQuantityProperty().asObject());
        ingredientMinColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new DoubleConverter()));
        ingredientMinColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            row.setMinQuantity(Math.max(0, safeDouble(evt.getNewValue())));
            persistIngredient(row);
        });

        ingredientPackageSizeColumn.setCellValueFactory(data -> data.getValue().packageSizeProperty().asObject());
        ingredientPackageSizeColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new DoubleConverter()));
        ingredientPackageSizeColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            row.setPackageSize(Math.max(0, safeDouble(evt.getNewValue())));
            persistIngredient(row);
            ingredientsTable.refresh();
            refreshRecipeCost();
        });

        ingredientPackagePriceColumn.setCellValueFactory(data -> data.getValue().packagePriceProperty().asObject());
        ingredientPackagePriceColumn.setCellFactory(col -> new IngredientAutoCommitCell<>(new DoubleConverter()));
        ingredientPackagePriceColumn.setOnEditCommit(evt -> {
            IngredientRow row = evt.getRowValue();
            row.setPackagePrice(Math.max(0, safeDouble(evt.getNewValue())));
            persistIngredient(row);
            ingredientsTable.refresh();
            refreshRecipeCost();
        });

        ingredientUnitCostColumn.setCellValueFactory(data -> data.getValue().unitCostProperty().asObject());

        ingredientsTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(IngredientRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("row-stock-low", "row-stock-zero", "row-inactive");
                if (empty || row == null) {
                    return;
                }
                if (!row.isActive()) {
                    getStyleClass().add("row-inactive");
                } else if (row.getStockQuantity() <= 0.0001) {
                    getStyleClass().add("row-stock-zero");
                } else if (row.getStockQuantity() <= row.getMinQuantity()) {
                    getStyleClass().add("row-stock-low");
                }
            }
        });
    }

    private void configureRecipeTable() {
        if (recipeTable == null) {
            return;
        }
        recipeTable.setItems(recipeRows);
        recipeTable.setEditable(true);

        recipeIngredientColumn.setCellValueFactory(data -> data.getValue().ingredientNameProperty());
        recipeUnitColumn.setCellValueFactory(data -> data.getValue().unitProperty());

        recipeQuantityColumn.setCellValueFactory(data -> data.getValue().quantityProperty().asObject());
        recipeQuantityColumn.setCellFactory(col -> new RecipeAutoCommitCell<>(new DoubleConverter()));
        recipeQuantityColumn.setOnEditCommit(evt -> {
            RecipeRow row = evt.getRowValue();
            double quantity = Math.max(0, safeDouble(evt.getNewValue()));
            if (quantity <= 0) {
                showToast("warning", "Quantite invalide");
                loadRecipeRows();
                return;
            }
            upsertRecipeLine(row.getIngredientId(), quantity);
        });

        recipeUnitCostColumn.setCellValueFactory(data -> data.getValue().unitCostProperty().asObject());
        recipeLineCostColumn.setCellValueFactory(data -> data.getValue().lineCostProperty().asObject());
    }

    private void configureRecipeSelectors() {
        if (recipeProductCombo != null) {
            recipeProductCombo.setItems(recipeProducts);
            recipeProductCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Product product) {
                    return product == null ? "" : product.getName();
                }

                @Override
                public Product fromString(String string) {
                    return null;
                }
            });
        }

        if (recipeIngredientCombo != null) {
            recipeIngredientCombo.setItems(ingredientRows.filtered(IngredientRow::isActive));
            recipeIngredientCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(IngredientRow ingredient) {
                    if (ingredient == null) {
                        return "";
                    }
                    return ingredient.getName() + " (" + ingredient.getUnit() + ")";
                }

                @Override
                public IngredientRow fromString(String string) {
                    return null;
                }
            });
        }
    }

    private void configureSearch() {
        if (searchField == null) {
            return;
        }
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredProducts.setPredicate(row -> {
                if (query.isBlank()) {
                    return true;
                }
                return row.getName().toLowerCase().contains(query);
            });
        });
    }

    private void loadThreshold() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String value = settingsDAO.getValue(STOCK_THRESHOLD_KEY);
                if (value == null || value.isBlank()) {
                    return DEFAULT_LOW_STOCK;
                }
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException ex) {
                    return DEFAULT_LOW_STOCK;
                }
            }
        };
        task.setOnSucceeded(evt -> {
            lowStockThreshold = task.getValue();
            productsTable.refresh();
        });
        Thread thread = new Thread(task, "stock-threshold");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadCategories() {
        CategoryDAO categoryDao = this.categoryDAO;
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                return categoryDao.findAll();
            }

            @Override
            protected void succeeded() {
                List<Category> categories = getValue();
                categoriesById.clear();
                categoryColors.clear();
                for (Category category : categories) {
                    categoriesById.put(category.getId(), category);
                    categoryColors.put(category.getId(), resolveCategoryColor(category));
                }
                updateCategoryColumn(categories);
                renderCategories(categories);
                loadProducts();
            }
        };
        task.setOnFailed(evt -> {
            LOG.error("Erreur categories", task.getException());
            showToast("error", "Categories indisponibles");
        });
        Thread thread = new Thread(task, "categories-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateCategoryColumn(List<Category> categories) {
        categoryColumn.setCellFactory(col -> new CategoryCell(categories));
    }

    private void loadProducts() {
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return productDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            Integer selectedProductId = getSelectedRecipeProductId();
            masterProducts.clear();
            List<Product> products = task.getValue();
            for (Product product : products) {
                Category category = categoriesById.get(product.getCategoryId());
                masterProducts.add(ProductRow.from(product, category));
            }
            recipeProducts.setAll(products);
            restoreRecipeProductSelection(selectedProductId);
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur produits", task.getException());
            showToast("error", "Produits indisponibles");
        });
        Thread thread = new Thread(task, "products-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadIngredients() {
        Integer selectedIngredientId = getSelectedIngredientId();
        Task<List<Ingredient>> task = new Task<>() {
            @Override
            protected List<Ingredient> call() throws Exception {
                return ingredientDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            ingredientRows.clear();
            ingredientById.clear();
            for (Ingredient ingredient : task.getValue()) {
                IngredientRow row = IngredientRow.from(ingredient);
                ingredientRows.add(row);
                ingredientById.put(row.getId(), row);
            }

            if (selectedIngredientId != null && ingredientsTable != null) {
                IngredientRow selected = ingredientById.get(selectedIngredientId);
                if (selected != null) {
                    ingredientsTable.getSelectionModel().select(selected);
                }
            }

            if (recipeIngredientCombo != null && recipeIngredientCombo.getSelectionModel().isEmpty()
                    && !recipeIngredientCombo.getItems().isEmpty()) {
                recipeIngredientCombo.getSelectionModel().selectFirst();
            }

            if (ingredientsTable != null) {
                ingredientsTable.refresh();
            }
            loadRecipeRows();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ingredients", task.getException());
            showToast("error", "Ingredients indisponibles");
        });
        Thread thread = new Thread(task, "ingredients-load");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onNewIngredient() {
        if (ingredientsTable == null) {
            return;
        }
        IngredientRow row = IngredientRow.newRow();
        ingredientRows.add(0, row);
        ingredientsTable.getSelectionModel().select(row);
        Platform.runLater(() -> ingredientsTable.edit(0, ingredientNameColumn));
    }

    @FXML
    private void onPurchaseIngredient() {
        if (ingredientsTable == null) {
            return;
        }
        IngredientRow row = ingredientsTable.getSelectionModel().getSelectedItem();
        if (row == null || row.isNew()) {
            showToast("warning", "Selectionnez un ingredient");
            return;
        }
        if (row.getPackageSize() <= 0) {
            showToast("warning", "Renseignez la taille du pack");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Achat ingredient");
        dialog.setHeaderText("Nombre de packs pour " + row.getName());
        dialog.setContentText("Packs:");
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }

        double packs = parseAmount(answer.get());
        if (packs <= 0) {
            showToast("warning", "Valeur invalide");
            return;
        }

        double quantity = packs * row.getPackageSize();
        double totalCost = packs * row.getPackagePrice();
        String purchaseDescription = "Achat ingredient: " + row.getName() + " (" + packs + " pack)";
        Integer userId = getCurrentUserId();
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    ingredientDAO.adjustStock(conn, row.getId(), quantity);
                    ingredientMovementDAO.insertMovement(
                            conn,
                            row.getId(),
                            quantity,
                            "PURCHASE",
                            row.getUnitCost(),
                            totalCost,
                            workPeriodId,
                            null,
                            userId
                    );
                            cashMovementDAO.insertMovement(
                                conn,
                                CashMovementDAO.TYPE_OUTFLOW,
                                CashMovementDAO.CATEGORY_INGREDIENT_PURCHASE,
                                totalCost,
                                purchaseDescription,
                                workPeriodId,
                                row.getId(),
                                userId
                            );
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", "Achat enregistre");
            loadIngredients();
        });
        runDbTask(task, "Achat impossible");
    }

    @FXML
    private void onWithdrawShoppingCash() {
        TextInputDialog amountDialog = new TextInputDialog();
        amountDialog.setTitle("Sortie caisse");
        amountDialog.setHeaderText("Montant sortie caisse (shopping)");
        amountDialog.setContentText("Montant:");
        Optional<String> amountAnswer = amountDialog.showAndWait();
        if (amountAnswer.isEmpty()) {
            return;
        }

        double amount = parseAmount(amountAnswer.get());
        if (amount <= 0) {
            showToast("warning", "Montant invalide");
            return;
        }

        TextInputDialog reasonDialog = new TextInputDialog("Shopping");
        reasonDialog.setTitle("Sortie caisse");
        reasonDialog.setHeaderText("Motif de la sortie");
        reasonDialog.setContentText("Motif:");
        Optional<String> reasonAnswer = reasonDialog.showAndWait();
        if (reasonAnswer.isEmpty()) {
            return;
        }

        String reason = safeString(reasonAnswer.get());
        if (reason.isBlank()) {
            reason = "Shopping";
        }

        Integer userId = getCurrentUserId();
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();
        String description = "Sortie caisse shopping: " + reason;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    cashMovementDAO.insertMovement(
                            conn,
                            CashMovementDAO.TYPE_OUTFLOW,
                            CashMovementDAO.CATEGORY_SHOPPING,
                            amount,
                            description,
                            workPeriodId,
                            null,
                            userId
                    );
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> showToast("success", "Sortie caisse enregistree"));
        runDbTask(task, "Sortie caisse impossible");
    }

    @FXML
    private void onRecipeProductChanged() {
        loadRecipeRows();
    }

    @FXML
    private void onAddRecipeLine() {
        Product product = recipeProductCombo == null ? null : recipeProductCombo.getSelectionModel().getSelectedItem();
        IngredientRow ingredient =
                recipeIngredientCombo == null ? null : recipeIngredientCombo.getSelectionModel().getSelectedItem();
        if (product == null || ingredient == null || ingredient.isNew()) {
            showToast("warning", "Selectionnez produit et ingredient");
            return;
        }
        double quantity = parseAmount(recipeQuantityField == null ? null : recipeQuantityField.getText());
        if (quantity <= 0) {
            showToast("warning", "Quantite invalide");
            return;
        }
        upsertRecipeLine(product.getId(), ingredient.getId(), quantity);
        if (recipeQuantityField != null) {
            recipeQuantityField.clear();
        }
    }

    @FXML
    private void onRemoveRecipeLine() {
        Product product = recipeProductCombo == null ? null : recipeProductCombo.getSelectionModel().getSelectedItem();
        RecipeRow selected = recipeTable == null ? null : recipeTable.getSelectionModel().getSelectedItem();
        if (product == null || selected == null) {
            showToast("warning", "Selectionnez une ligne de recette");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productIngredientDAO.deleteRecipeLine(product.getId(), selected.getIngredientId());
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", "Ligne supprimee");
            loadRecipeRows();
        });
        runDbTask(task, "Suppression recette impossible");
    }

    private void persistIngredient(IngredientRow row) {
        if (row == null) {
            return;
        }
        row.setName(safeString(row.getName()));
        row.setUnit(normalizeUnit(row.getUnit()));
        row.setPackageSize(Math.max(0, row.getPackageSize()));
        row.setPackagePrice(Math.max(0, row.getPackagePrice()));
        row.setStockQuantity(Math.max(0, row.getStockQuantity()));
        row.setMinQuantity(Math.max(0, row.getMinQuantity()));

        if (row.getName().isBlank()) {
            return;
        }
        if (row.isNew()) {
            createIngredient(row);
        } else {
            updateIngredient(row);
        }
    }

    private void createIngredient(IngredientRow row) {
        if (row.isSaving()) {
            return;
        }
        row.setSaving(true);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return ingredientDAO.insertIngredient(row.toIngredient());
            }
        };
        task.setOnSucceeded(evt -> {
            row.setSaving(false);
            int id = task.getValue();
            if (id <= 0) {
                showToast("error", "Ajout ingredient impossible");
                return;
            }
            row.setId(id);
            row.setNew(false);
            showToast("success", "Ingredient ajoute");
            loadIngredients();
        });
        task.setOnFailed(evt -> row.setSaving(false));
        runDbTask(task, "Ajout ingredient impossible");
    }

    private void updateIngredient(IngredientRow row) {
        if (row.isSaving()) {
            return;
        }
        row.setSaving(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ingredientDAO.updateIngredient(row.toIngredient());
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            row.setSaving(false);
            loadIngredients();
        });
        task.setOnFailed(evt -> row.setSaving(false));
        runDbTask(task, "Mise a jour ingredient impossible");
    }

    private void loadRecipeRows() {
        Product product = recipeProductCombo == null ? null : recipeProductCombo.getSelectionModel().getSelectedItem();
        if (product == null) {
            recipeRows.clear();
            refreshRecipeCost();
            return;
        }

        Task<List<ProductIngredientUsage>> task = new Task<>() {
            @Override
            protected List<ProductIngredientUsage> call() throws Exception {
                return productIngredientDAO.findRecipeByProduct(product.getId());
            }
        };
        task.setOnSucceeded(evt -> {
            recipeRows.clear();
            for (ProductIngredientUsage usage : task.getValue()) {
                recipeRows.add(RecipeRow.from(usage));
            }
            refreshRecipeCost();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur recette", task.getException());
            showToast("error", "Recette indisponible");
        });
        Thread thread = new Thread(task, "recipe-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void upsertRecipeLine(int productId, int ingredientId, double quantity) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productIngredientDAO.upsertRecipeLine(productId, ingredientId, quantity);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", "Recette mise a jour");
            loadRecipeRows();
        });
        runDbTask(task, "Mise a jour recette impossible");
    }

    private void upsertRecipeLine(int ingredientId, double quantity) {
        Product product = recipeProductCombo == null ? null : recipeProductCombo.getSelectionModel().getSelectedItem();
        if (product == null) {
            return;
        }
        upsertRecipeLine(product.getId(), ingredientId, quantity);
    }

    private void refreshRecipeCost() {
        double totalCost = recipeRows.stream().mapToDouble(RecipeRow::getLineCost).sum();
        if (recipeEstimatedCostLabel != null) {
            recipeEstimatedCostLabel.setText("Cout recette: " + formatMoney(totalCost));
        }
        if (recipeTable != null) {
            recipeTable.refresh();
        }
    }

    private Integer getSelectedIngredientId() {
        if (ingredientsTable == null) {
            return null;
        }
        IngredientRow selected = ingredientsTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isNew()) {
            return null;
        }
        return selected.getId();
    }

    private Integer getSelectedRecipeProductId() {
        if (recipeProductCombo == null) {
            return null;
        }
        Product selected = recipeProductCombo.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void restoreRecipeProductSelection(Integer selectedProductId) {
        if (recipeProductCombo == null) {
            return;
        }
        Product toSelect = null;
        if (selectedProductId != null) {
            for (Product product : recipeProducts) {
                if (product.getId() == selectedProductId) {
                    toSelect = product;
                    break;
                }
            }
        }
        if (toSelect == null && !recipeProducts.isEmpty()) {
            toSelect = recipeProducts.get(0);
        }
        recipeProductCombo.getSelectionModel().select(toSelect);
        loadRecipeRows();
    }

    @FXML
    private void onNewProduct() {
        ProductRow row = ProductRow.newRow(defaultCategory());
        masterProducts.add(0, row);
        productsTable.getSelectionModel().select(row);
        Platform.runLater(() -> productsTable.edit(0, nameColumn));
    }

    private Category defaultCategory() {
        return categoriesById.values().stream()
                .min(Comparator.comparingInt(Category::getSortOrder))
                .orElse(null);
    }

    private void maybeCreateProduct(ProductRow row) {
        if (!row.isNew()) {
            return;
        }
        if (row.getName().isBlank() || row.getCategory() == null) {
            return;
        }
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                Product product = new Product(
                        0,
                        row.getName(),
                        row.getPrice(),
                        row.getCost(),
                        row.getCategory().getId(),
                        row.getStock(),
                    row.isActive(),
                    row.isPrepared()
                );
                return productDAO.insertProduct(product);
            }
        };
        task.setOnSucceeded(evt -> {
            int id = task.getValue();
            if (id <= 0) {
                showToast("error", "Creation echouee");
                return;
            }
            row.setId(id);
            row.setNew(false);
            showToast("success", "Produit ajoute");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout produit", task.getException());
            showToast("error", "Ajout produit impossible");
        });
        Thread thread = new Thread(task, "product-add");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateProductName(ProductRow row) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updateName(row.getId(), row.getName());
                return null;
            }
        };
        runDbTask(task, "Erreur maj nom");
    }

    private void updateProductCategory(ProductRow row, int categoryId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updateCategory(row.getId(), categoryId);
                return null;
            }
        };
        runDbTask(task, "Erreur maj categorie");
    }

    private void updateProductCost(ProductRow row) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updateCost(row.getId(), row.getCost());
                return null;
            }
        };
        runDbTask(task, "Erreur maj cout");
    }

    private void updateProductPrice(ProductRow row) {
        Integer userId = getCurrentUserId();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updatePriceWithHistory(row.getId(), row.getPrice(), userId);
                return null;
            }
        };
        runDbTask(task, "Erreur maj prix");
    }

    private void updateProductActive(ProductRow row) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updateActive(row.getId(), row.isActive());
                return null;
            }
        };
        runDbTask(task, "Erreur maj actif");
    }

    private void updateProductPrepared(ProductRow row) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.updatePrepared(row.getId(), row.isPrepared());
                return null;
            }
        };
        runDbTask(task, "Erreur maj type produit");
    }

    private void adjustStock(ProductRow row, int delta) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    productDAO.adjustStock(conn, row.getId(), delta);
                    stockMovementDAO.insertMovement(conn, row.getId(), delta, "Ajustement");
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            row.setStock(Math.max(0, row.getStock() + delta));
            productsTable.refresh();
        });
        runDbTask(task, "Erreur stock");
    }

    private void setStock(ProductRow row, int newValue) {
        int delta = newValue - row.getStock();
        if (delta == 0) {
            return;
        }
        adjustStock(row, delta);
    }

    private void overrideIngredientStock(IngredientRow row, double newQuantity) {
        double previousQuantity = row.getStockQuantity();
        double delta = newQuantity - previousQuantity;
        row.setStockQuantity(newQuantity);
        if (Math.abs(delta) < 0.000001) {
            return;
        }

        Integer userId = getCurrentUserId();
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (var conn = com.cafepos.db.DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    ingredientDAO.setStockQuantity(conn, row.getId(), newQuantity);
                    ingredientMovementDAO.insertMovement(
                            conn,
                            row.getId(),
                            delta,
                            "MANUAL",
                            row.getUnitCost(),
                            delta * row.getUnitCost(),
                            workPeriodId,
                            null,
                            userId
                    );
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", "Stock ingredient mis a jour");
            loadIngredients();
        });
        runDbTask(task, "Override stock ingredient impossible");
    }

    private void loadSupplements() {
        TagGroupDAO tagGroupDao = this.tagGroupDAO;
        TagDAO tagDao = this.tagDAO;
        Task<List<TagGroup>> task = new Task<>() {
            @Override
            protected List<TagGroup> call() throws Exception {
                List<TagGroup> groups = tagGroupDao.findAll();
                for (TagGroup group : groups) {
                    List<Tag> tags = tagDao.findByGroupId(group.getId());
                    for (Tag tag : tags) {
                        group.addTag(tag);
                    }
                }
                return groups;
            }
        };
        task.setOnSucceeded(evt -> renderSupplements(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur supplements", task.getException());
            showToast("error", "Supplements indisponibles");
        });
        Thread thread = new Thread(task, "supplements-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderSupplements(List<TagGroup> groups) {
        supplementsBox.getChildren().clear();
        for (TagGroup group : groups) {
            supplementsBox.getChildren().add(buildTagGroupBlock(group));
        }
        supplementsBox.getChildren().add(buildAddGroupRow());
    }

    private VBox buildTagGroupBlock(TagGroup group) {
        VBox wrapper = new VBox(6);
        wrapper.getStyleClass().add("card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("> " + group.getName());
        title.getStyleClass().add("subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label mode = new Label(group.isMultiSelect() ? "Choix multiple" : "Choix unique");
        mode.getStyleClass().add("hint-label");
        Button edit = new Button("Edit");
        edit.getStyleClass().add("ghost-button");
        Button delete = new Button("X");
        delete.getStyleClass().add("ghost-button");
        header.getChildren().addAll(title, spacer, mode, edit, delete);

        VBox tagList = new VBox(6);
        for (Tag tag : group.getTags()) {
            tagList.getChildren().add(buildTagRow(tag));
        }
        tagList.getChildren().add(buildAddTagRow(group));

        final boolean[] expanded = {true};
        title.setOnMouseClicked(event -> {
            expanded[0] = !expanded[0];
            title.setText((expanded[0] ? "> " : "v ") + group.getName());
            tagList.setVisible(expanded[0]);
            tagList.setManaged(expanded[0]);
        });

        edit.setOnAction(event -> editTagGroup(group, title));
        delete.setOnAction(event -> deleteTagGroup(group));

        wrapper.getChildren().addAll(header, tagList);
        return wrapper;
    }

    private void editTagGroup(TagGroup group, Label title) {
        TagGroupDAO tagGroupDao = this.tagGroupDAO;
        TextField field = new TextField(group.getName());
        CheckBox multi = new CheckBox("Multi");
        multi.setSelected(group.isMultiSelect());
        Button save = new Button("OK");
        save.getStyleClass().add("action-button");

        HBox editor = new HBox(8, field, multi, save);
        editor.setAlignment(Pos.CENTER_LEFT);

        int index = supplementsBox.getChildren().indexOf(title.getParent().getParent());
        if (index < 0) {
            return;
        }

        save.setOnAction(evt -> {
            String name = safeString(field.getText());
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    tagGroupDao.updateName(group.getId(), name);
                    tagGroupDao.updateMultiSelect(group.getId(), multi.isSelected());
                    return null;
                }
            };
            task.setOnSucceeded(done -> loadSupplements());
            runDbTask(task, "Erreur groupe");
        });

        VBox wrapper = (VBox) title.getParent().getParent();
        wrapper.getChildren().add(1, editor);
    }

    private void deleteTagGroup(TagGroup group) {
        TagGroupDAO tagGroupDao = this.tagGroupDAO;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                tagGroupDao.deleteGroup(group.getId());
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadSupplements());
        runDbTask(task, "Suppression impossible");
    }

    private HBox buildTagRow(Tag tag) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(44);
        Label drag = new Label("::");
        drag.getStyleClass().add("hint-label");
        Label name = new Label(tag.getName());
        HBox.setHgrow(name, Priority.ALWAYS);
        Label price = new Label(formatMoney(tag.getPriceModifier()));
        price.setMinWidth(70);
        price.setTextAlignment(TextAlignment.RIGHT);
        Button delete = new Button("X");
        delete.getStyleClass().add("ghost-button");

        name.setOnMouseClicked(event -> editTagName(tag, name));
        price.setOnMouseClicked(event -> editTagPrice(tag, price));
        delete.setOnAction(event -> deleteTag(tag));

        row.getChildren().addAll(drag, name, new Label("+"), price, delete);
        return row;
    }

    private HBox buildAddTagRow(TagGroup group) {
        TagDAO tagDao = this.tagDAO;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(44);
        TextField nameField = new TextField();
        nameField.setPromptText("Nom du tag");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        TextField priceField = new TextField();
        priceField.setPromptText("+0 DZD");
        priceField.setPrefWidth(80);
        Button add = new Button("Ajouter");
        add.getStyleClass().add("action-button");
        add.setOnAction(event -> {
            String name = safeString(nameField.getText());
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            double price = parseAmount(priceField.getText());
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    tagDao.insertTag(group.getId(), name, price);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> loadSupplements());
            runDbTask(task, "Ajout tag impossible");
        });
        row.getChildren().addAll(nameField, priceField, add);
        return row;
    }

    private HBox buildAddGroupRow() {
        TagGroupDAO tagGroupDao = this.tagGroupDAO;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));
        TextField field = new TextField();
        field.setPromptText("Nom du groupe");
        HBox.setHgrow(field, Priority.ALWAYS);
        CheckBox multi = new CheckBox("Multi");
        Button add = new Button("Ajouter");
        add.getStyleClass().add("action-button");
        add.setOnAction(event -> {
            String name = safeString(field.getText());
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    tagGroupDao.insertGroup(name, multi.isSelected());
                    return null;
                }
            };
            task.setOnSucceeded(evt -> loadSupplements());
            runDbTask(task, "Ajout groupe impossible");
        });
        row.getChildren().addAll(field, multi, add);
        return row;
    }

    private void editTagName(Tag tag, Label label) {
        TagDAO tagDao = this.tagDAO;
        TextField editor = new TextField(tag.getName());
        replaceInline(label, editor, value -> {
            String name = safeString(value);
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    tagDao.updateName(tag.getId(), name);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> loadSupplements());
            runDbTask(task, "Erreur tag");
        });
    }

    private void editTagPrice(Tag tag, Label label) {
        TagDAO tagDao = this.tagDAO;
        TextField editor = new TextField(String.valueOf(tag.getPriceModifier()));
        replaceInline(label, editor, value -> {
            double price = parseAmount(value);
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    tagDao.updatePrice(tag.getId(), price);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> loadSupplements());
            runDbTask(task, "Erreur tag");
        });
    }

    private void deleteTag(Tag tag) {
        TagDAO tagDao = this.tagDAO;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                tagDao.deleteTag(tag.getId());
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadSupplements());
        runDbTask(task, "Suppression impossible");
    }

    private void renderCategories(List<Category> categories) {
        categoriesBox.getChildren().clear();
        List<Category> sorted = new ArrayList<>(categories);
        sorted.sort(Comparator.comparingInt(Category::getSortOrder));
        for (Category category : sorted) {
            categoriesBox.getChildren().add(buildCategoryRow(category));
        }
        categoriesBox.getChildren().add(buildAddCategoryRow());
    }

    private HBox buildCategoryRow(Category category) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(48);
        row.setUserData(category.getId());

        Label drag = new Label("::");
        drag.getStyleClass().add("hint-label");

        ColorPicker picker = new ColorPicker(colorFromStored(categoryColors.get(category.getId())));
        picker.setOnAction(event -> saveCategoryColor(category.getId(), picker.getValue()));

        Label name = new Label(category.getName());
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setOnMouseClicked(event -> editCategoryName(category, name));

        Button delete = new Button("X");
        delete.getStyleClass().add("ghost-button");
        delete.setOnAction(event -> deleteCategory(category));

        row.getChildren().addAll(drag, picker, name, delete);

        row.setOnDragDetected(event -> {
            Dragboard board = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(category.getId()));
            board.setContent(content);
            event.consume();
        });

        row.setOnDragOver(event -> {
            if (event.getGestureSource() != row && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        row.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasString()) {
                int sourceId = Integer.parseInt(board.getString());
                int targetId = category.getId();
                reorderCategories(sourceId, targetId);
            }
            event.setDropCompleted(true);
            event.consume();
        });

        return row;
    }

    private HBox buildAddCategoryRow() {
        CategoryDAO categoryDao = this.categoryDAO;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        TextField field = new TextField();
        field.setPromptText("Nouvelle catégorie");
        HBox.setHgrow(field, Priority.ALWAYS);
        Button add = new Button("Ajouter");
        add.getStyleClass().add("action-button");
        add.setOnAction(event -> {
            String name = safeString(field.getText());
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            Task<Integer> task = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    int order = categoryDao.getMaxSortOrder() + 1;
                    return categoryDao.insertCategory(name, order);
                }
            };
            task.setOnSucceeded(evt -> {
                field.clear();
                loadCategories();
            });
            runDbTask(task, "Ajout categorie impossible");
        });
        row.getChildren().addAll(field, add);
        return row;
    }

    private void editCategoryName(Category category, Label label) {
        CategoryDAO categoryDao = this.categoryDAO;
        TextField editor = new TextField(category.getName());
        replaceInline(label, editor, value -> {
            String name = safeString(value);
            if (name.isBlank()) {
                showToast("warning", "Nom requis");
                return;
            }
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    categoryDao.updateName(category.getId(), name);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> loadCategories());
            runDbTask(task, "Erreur categorie");
        });
    }

    private void deleteCategory(Category category) {
        CategoryDAO categoryDao = this.categoryDAO;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if (productDAO.countByCategory(category.getId()) > 0) {
                    return false;
                }
                categoryDao.deleteCategory(category.getId());
                return true;
            }
        };
        task.setOnSucceeded(evt -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                loadCategories();
            } else {
                showToast("warning", "Categorie utilisee");
            }
        });
        runDbTask(task, "Suppression impossible");
    }

    private void reorderCategories(int sourceId, int targetId) {
        CategoryDAO categoryDao = this.categoryDAO;
        List<Category> current = new ArrayList<>(categoriesById.values());
        current.sort(Comparator.comparingInt(Category::getSortOrder));
        Category source = categoriesById.get(sourceId);
        Category target = categoriesById.get(targetId);
        if (source == null || target == null) {
            return;
        }
        current.remove(source);
        int targetIndex = current.indexOf(target);
        if (targetIndex < 0) {
            targetIndex = current.size();
        }
        current.add(targetIndex, source);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int order = 1;
                for (Category category : current) {
                    categoryDao.updateSortOrder(category.getId(), order++);
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> loadCategories());
        runDbTask(task, "Reordre impossible");
    }

    private void saveCategoryColor(int categoryId, Color color) {
        String hex = colorToHex(color);
        CategoryDAO categoryDao = this.categoryDAO;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                categoryDao.updateColor(categoryId, hex);
                return null;
            }
        };
        task.setOnSucceeded(evt -> categoryColors.put(categoryId, hex));
        runDbTask(task, "Erreur couleur");
    }

    private Color colorFromStored(String value) {
        if (value == null || value.isBlank()) {
            return Color.web("#6B2D1A");
        }
        try {
            return Color.web(value.trim());
        } catch (Exception ex) {
            return Color.web("#6B2D1A");
        }
    }

    private String resolveCategoryColor(Category category) {
        if (category == null) {
            return "#6B2D1A";
        }
        String stored = category.getColor();
        if (stored != null && !stored.isBlank()) {
            return stored.trim();
        }
        return defaultCategoryColor(category.getName());
    }

    private String defaultCategoryColor(String categoryName) {
        if (categoryName == null) {
            return "#6B2D1A";
        }
        String normalized = categoryName.trim().toLowerCase();
        if (normalized.equals("hot beverages")) {
            return "#6B2D1A";
        }
        if (normalized.equals("cold beverages")) {
            return "#1A4A6B";
        }
        if (normalized.equals("sweets")) {
            return "#A0522D";
        }
        if (normalized.equals("salties")) {
            return "#7A4A1A";
        }
        if (normalized.equals("cards")) {
            return "#2E5A2E";
        }
        if (normalized.equals("additions")) {
            return "#4A3A6B";
        }
        return "#6B2D1A";
    }

    private String colorToHex(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private String formatMoney(double value) {
        return FormatUtils.formatMoney(value);
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double safeDouble(Double value) {
        return value == null ? 0 : value;
    }

    private String normalizeUnit(String value) {
        String normalized = safeString(value).toUpperCase();
        return normalized.isBlank() ? "UNIT" : normalized;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer getCurrentUserId() {
        User user = SessionManager.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private void replaceInline(Label label, TextField editor, java.util.function.Consumer<String> onCommit) {
        HBox parent = (HBox) label.getParent();
        int index = parent.getChildren().indexOf(label);
        parent.getChildren().set(index, editor);
        Platform.runLater(editor::requestFocus);
        editor.setOnAction(event -> {
            parent.getChildren().set(index, label);
            onCommit.accept(editor.getText());
        });
        editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                parent.getChildren().set(index, label);
                onCommit.accept(editor.getText());
            }
        });
    }

    private void runDbTask(Task<?> task, String errorMessage) {
        var previousOnFailed = task.getOnFailed();
        task.setOnFailed(evt -> {
            if (previousOnFailed != null) {
                previousOnFailed.handle(evt);
            }
            LOG.error(errorMessage, task.getException());
            showToast("error", errorMessage);
        });
        Thread thread = new Thread(task, "db-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void showToast(String type, String message) {
        if (toastContainer == null) {
            return;
        }
        HBox toast = new HBox(8);
        toast.getStyleClass().add("toast");
        if (type != null && !type.isBlank()) {
            toast.getStyleClass().add(type);
        }
        Label icon = new Label(iconFor(type));
        Label text = new Label(message == null ? "" : message);
        text.setWrapText(true);
        toast.getChildren().addAll(icon, text);

        toastContainer.getChildren().add(0, toast);
        while (toastContainer.getChildren().size() > MAX_TOASTS) {
            toastContainer.getChildren().remove(toastContainer.getChildren().size() - 1);
        }

        PauseTransition delay = new PauseTransition(Duration.millis(3000));
        delay.setOnFinished(evt -> fadeOutToast(toast));
        delay.play();
    }

    private void fadeOutToast(HBox toast) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), toast);
        fade.setToValue(0);
        fade.setOnFinished(evt -> toastContainer.getChildren().remove(toast));
        fade.play();
    }

    private String iconFor(String type) {
        return switch (type == null ? "" : type) {
            case "success" -> "OK";
            case "warning" -> "!";
            case "error" -> "X";
            default -> "i";
        };
    }

    private static class DoubleConverter extends StringConverter<Double> {
        @Override
        public String toString(Double object) {
            if (object == null) {
                return "";
            }
            return String.valueOf(object);
        }

        @Override
        public Double fromString(String string) {
            if (string == null || string.isBlank()) {
                return 0.0;
            }
            try {
                return Double.parseDouble(string.replace(',', '.'));
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
    }

    private class IngredientActiveCell extends TableCell<IngredientRow, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        IngredientActiveCell() {
            checkBox.setOnAction(event -> {
                IngredientRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    return;
                }
                row.setActive(checkBox.isSelected());
                persistIngredient(row);
                if (ingredientsTable != null) {
                    ingredientsTable.refresh();
                }
            });
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(Boolean.TRUE.equals(value));
            setGraphic(checkBox);
        }
    }

    private class IngredientAutoCommitCell<T> extends TableCell<IngredientRow, T> {
        private final TextField editor = new TextField();
        private final StringConverter<T> converter;

        IngredientAutoCommitCell(StringConverter<T> converter) {
            this.converter = converter;
            editor.setOnAction(event -> commit());
            editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    commit();
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            editor.setText(converter.toString(getItem()));
            setGraphic(editor);
            setText(null);
            Platform.runLater(editor::requestFocus);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(converter.toString(getItem()));
            setGraphic(null);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                setText(null);
                return;
            }
            if (isEditing()) {
                editor.setText(converter.toString(item));
                setGraphic(editor);
                setText(null);
            } else {
                setText(converter.toString(item));
                setGraphic(null);
            }
        }

        private void commit() {
            if (!isEditing()) {
                return;
            }
            commitEdit(converter.fromString(editor.getText()));
        }
    }

    private class RecipeAutoCommitCell<T> extends TableCell<RecipeRow, T> {
        private final TextField editor = new TextField();
        private final StringConverter<T> converter;

        RecipeAutoCommitCell(StringConverter<T> converter) {
            this.converter = converter;
            editor.setOnAction(event -> commit());
            editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    commit();
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            editor.setText(converter.toString(getItem()));
            setGraphic(editor);
            setText(null);
            Platform.runLater(editor::requestFocus);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(converter.toString(getItem()));
            setGraphic(null);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                setText(null);
                return;
            }
            if (isEditing()) {
                editor.setText(converter.toString(item));
                setGraphic(editor);
                setText(null);
            } else {
                setText(converter.toString(item));
                setGraphic(null);
            }
        }

        private void commit() {
            if (!isEditing()) {
                return;
            }
            commitEdit(converter.fromString(editor.getText()));
        }
    }

    public static class IngredientRow {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty name = new SimpleStringProperty("");
        private final StringProperty unit = new SimpleStringProperty("UNIT");
        private final DoubleProperty packageSize = new SimpleDoubleProperty(1);
        private final DoubleProperty packagePrice = new SimpleDoubleProperty(0);
        private final DoubleProperty stockQuantity = new SimpleDoubleProperty(0);
        private final DoubleProperty minQuantity = new SimpleDoubleProperty(0);
        private final DoubleProperty unitCost = new SimpleDoubleProperty(0);
        private final BooleanProperty active = new SimpleBooleanProperty(true);
        private boolean isNew;
        private boolean saving;

        IngredientRow() {
            packageSize.addListener((obs, oldVal, newVal) -> recalcUnitCost());
            packagePrice.addListener((obs, oldVal, newVal) -> recalcUnitCost());
            recalcUnitCost();
        }

        public static IngredientRow from(Ingredient ingredient) {
            IngredientRow row = new IngredientRow();
            row.setId(ingredient.getId());
            row.setName(ingredient.getName());
            row.setUnit(ingredient.getUnit());
            row.setPackageSize(ingredient.getPackageSize());
            row.setPackagePrice(ingredient.getPackagePrice());
            row.setStockQuantity(ingredient.getStockQuantity());
            row.setMinQuantity(ingredient.getMinQuantity());
            row.setActive(ingredient.isActive());
            row.setNew(false);
            row.recalcUnitCost();
            return row;
        }

        public static IngredientRow newRow() {
            IngredientRow row = new IngredientRow();
            row.setNew(true);
            return row;
        }

        public Ingredient toIngredient() {
            return new Ingredient(
                    getId(),
                    getName(),
                    getUnit(),
                    Math.max(0, getPackageSize()),
                    Math.max(0, getPackagePrice()),
                    Math.max(0, getStockQuantity()),
                    Math.max(0, getMinQuantity()),
                    isActive()
            );
        }

        private void recalcUnitCost() {
            if (getPackageSize() <= 0) {
                setUnitCost(0);
            } else {
                setUnitCost(getPackagePrice() / getPackageSize());
            }
        }

        public int getId() {
            return id.get();
        }

        public void setId(int value) {
            id.set(value);
        }

        public IntegerProperty idProperty() {
            return id;
        }

        public String getName() {
            return name.get();
        }

        public void setName(String value) {
            name.set(value);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public String getUnit() {
            return unit.get();
        }

        public void setUnit(String value) {
            unit.set(value);
        }

        public StringProperty unitProperty() {
            return unit;
        }

        public double getPackageSize() {
            return packageSize.get();
        }

        public void setPackageSize(double value) {
            packageSize.set(value);
        }

        public DoubleProperty packageSizeProperty() {
            return packageSize;
        }

        public double getPackagePrice() {
            return packagePrice.get();
        }

        public void setPackagePrice(double value) {
            packagePrice.set(value);
        }

        public DoubleProperty packagePriceProperty() {
            return packagePrice;
        }

        public double getStockQuantity() {
            return stockQuantity.get();
        }

        public void setStockQuantity(double value) {
            stockQuantity.set(value);
        }

        public DoubleProperty stockQuantityProperty() {
            return stockQuantity;
        }

        public double getMinQuantity() {
            return minQuantity.get();
        }

        public void setMinQuantity(double value) {
            minQuantity.set(value);
        }

        public DoubleProperty minQuantityProperty() {
            return minQuantity;
        }

        public double getUnitCost() {
            return unitCost.get();
        }

        public void setUnitCost(double value) {
            unitCost.set(value);
        }

        public DoubleProperty unitCostProperty() {
            return unitCost;
        }

        public boolean isActive() {
            return active.get();
        }

        public void setActive(boolean value) {
            active.set(value);
        }

        public BooleanProperty activeProperty() {
            return active;
        }

        public boolean isNew() {
            return isNew;
        }

        public void setNew(boolean value) {
            isNew = value;
        }

        public boolean isSaving() {
            return saving;
        }

        public void setSaving(boolean value) {
            saving = value;
        }
    }

    public static class RecipeRow {
        private final IntegerProperty ingredientId = new SimpleIntegerProperty();
        private final StringProperty ingredientName = new SimpleStringProperty("");
        private final StringProperty unit = new SimpleStringProperty("");
        private final DoubleProperty quantity = new SimpleDoubleProperty(0);
        private final DoubleProperty unitCost = new SimpleDoubleProperty(0);
        private final DoubleProperty lineCost = new SimpleDoubleProperty(0);

        RecipeRow() {
            quantity.addListener((obs, oldVal, newVal) -> recalcLineCost());
            unitCost.addListener((obs, oldVal, newVal) -> recalcLineCost());
            recalcLineCost();
        }

        public static RecipeRow from(ProductIngredientUsage usage) {
            RecipeRow row = new RecipeRow();
            row.setIngredientId(usage.ingredientId());
            row.setIngredientName(usage.ingredientName());
            row.setUnit(usage.unit());
            row.setQuantity(usage.quantityPerProduct());
            row.setUnitCost(usage.unitCost());
            row.recalcLineCost();
            return row;
        }

        private void recalcLineCost() {
            setLineCost(getQuantity() * getUnitCost());
        }

        public int getIngredientId() {
            return ingredientId.get();
        }

        public void setIngredientId(int value) {
            ingredientId.set(value);
        }

        public IntegerProperty ingredientIdProperty() {
            return ingredientId;
        }

        public String getIngredientName() {
            return ingredientName.get();
        }

        public void setIngredientName(String value) {
            ingredientName.set(value);
        }

        public StringProperty ingredientNameProperty() {
            return ingredientName;
        }

        public String getUnit() {
            return unit.get();
        }

        public void setUnit(String value) {
            unit.set(value);
        }

        public StringProperty unitProperty() {
            return unit;
        }

        public double getQuantity() {
            return quantity.get();
        }

        public void setQuantity(double value) {
            quantity.set(value);
        }

        public DoubleProperty quantityProperty() {
            return quantity;
        }

        public double getUnitCost() {
            return unitCost.get();
        }

        public void setUnitCost(double value) {
            unitCost.set(value);
        }

        public DoubleProperty unitCostProperty() {
            return unitCost;
        }

        public double getLineCost() {
            return lineCost.get();
        }

        public void setLineCost(double value) {
            lineCost.set(value);
        }

        public DoubleProperty lineCostProperty() {
            return lineCost;
        }
    }

    private class ActiveCell extends TableCell<ProductRow, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        ActiveCell() {
            checkBox.setOnAction(event -> {
                ProductRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    return;
                }
                row.setActive(checkBox.isSelected());
                if (!row.isNew()) {
                    updateProductActive(row);
                }
                productsTable.refresh();
            });
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(Boolean.TRUE.equals(value));
            setGraphic(checkBox);
        }
    }

    private class PreparedCell extends TableCell<ProductRow, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        PreparedCell() {
            checkBox.setOnAction(event -> {
                ProductRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    return;
                }
                row.setPrepared(checkBox.isSelected());
                if (row.isNew()) {
                    maybeCreateProduct(row);
                } else {
                    updateProductPrepared(row);
                }
            });
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            checkBox.setSelected(Boolean.TRUE.equals(value));
            setGraphic(checkBox);
        }
    }

    private class CategoryCell extends TableCell<ProductRow, Category> {
        private final javafx.scene.control.ComboBox<Category> combo;

        CategoryCell() {
            this(FXCollections.observableArrayList());
        }

        CategoryCell(List<Category> categories) {
            combo = new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList(categories));
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setOnAction(event -> {
                ProductRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    return;
                }
                Category selected = combo.getSelectionModel().getSelectedItem();
                row.setCategory(selected);
                if (row.isNew()) {
                    maybeCreateProduct(row);
                } else if (selected != null) {
                    updateProductCategory(row, selected.getId());
                }
            });
        }

        @Override
        protected void updateItem(Category value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            combo.getSelectionModel().select(value);
            setGraphic(combo);
        }
    }

    private class AutoCommitCell<T> extends TableCell<ProductRow, T> {
        private final TextField editor = new TextField();
        private final StringConverter<T> converter;

        AutoCommitCell(StringConverter<T> converter) {
            this.converter = converter;
            editor.setOnAction(event -> commit());
            editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    commit();
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (getItem() == null) {
                return;
            }
            editor.setText(converter.toString(getItem()));
            setGraphic(editor);
            setText(null);
            Platform.runLater(editor::requestFocus);
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(converter.toString(getItem()));
            setGraphic(null);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                setText(null);
                return;
            }
            if (isEditing()) {
                editor.setText(converter.toString(item));
                setGraphic(editor);
                setText(null);
            } else {
                setText(converter.toString(item));
                setGraphic(null);
            }
        }

        private void commit() {
            if (!isEditing()) {
                return;
            }
            T value = converter.fromString(editor.getText());
            commitEdit(value);
        }
    }

    private class StockCell extends TableCell<ProductRow, Integer> {
        private final HBox root = new HBox(6);
        private final Button minus = new Button("-");
        private final Label valueLabel = new Label();
        private final Button plus = new Button("+");
        private final Button plusTen = new Button("+10");

        StockCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            valueLabel.setMinWidth(32);
            valueLabel.setAlignment(Pos.CENTER);
            plusTen.getStyleClass().add("ghost-button");

            minus.setOnAction(event -> change(-1));
            plus.setOnAction(event -> change(1));
            plusTen.setOnAction(event -> change(10));

            valueLabel.setOnMouseClicked(event -> showInlineEditor());

            root.getChildren().addAll(minus, valueLabel, plus, plusTen);
        }

        @Override
        protected void updateItem(Integer value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }
            valueLabel.setText(String.valueOf(value == null ? 0 : value));
            setGraphic(root);
        }

        private void change(int delta) {
            ProductRow row = getTableRow().getItem();
            if (row == null || row.isNew()) {
                return;
            }
            adjustStock(row, delta);
        }

        private void showInlineEditor() {
            ProductRow row = getTableRow().getItem();
            if (row == null || row.isNew()) {
                return;
            }
            TextField editor = new TextField(String.valueOf(row.getStock()));
            editor.setPrefWidth(60);
            int index = root.getChildren().indexOf(valueLabel);
            root.getChildren().set(index, editor);
            Platform.runLater(editor::requestFocus);
            editor.setOnAction(event -> commitEditor(editor, index));
            editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    commitEditor(editor, index);
                }
            });
        }

        private void commitEditor(TextField editor, int index) {
            root.getChildren().set(index, valueLabel);
            int value;
            try {
                value = Integer.parseInt(editor.getText().trim());
            } catch (NumberFormatException ignored) {
                return;
            }
            ProductRow row = getTableRow().getItem();
            if (row != null) {
                setStock(row, Math.max(0, value));
            }
        }
    }

    public static class ProductRow {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty name = new SimpleStringProperty("");
        private final DoubleProperty price = new SimpleDoubleProperty(0);
        private final DoubleProperty cost = new SimpleDoubleProperty(0);
        private final IntegerProperty stock = new SimpleIntegerProperty(0);
        private final BooleanProperty active = new SimpleBooleanProperty(true);
        private final BooleanProperty prepared = new SimpleBooleanProperty(false);
        private final ObjectProperty<Category> category = new SimpleObjectProperty<>();
        private boolean isNew;

        public static ProductRow from(Product product, Category category) {
            ProductRow row = new ProductRow();
            row.setId(product.getId());
            row.setName(product.getName());
            row.setPrice(product.getPrice());
            row.setCost(product.getCost());
            row.setStock(product.getStock());
            row.setActive(product.isActive());
            row.setPrepared(product.isPrepared());
            row.setCategory(category);
            row.setNew(false);
            return row;
        }

        public static ProductRow newRow(Category category) {
            ProductRow row = new ProductRow();
            row.setCategory(category);
            row.setNew(true);
            return row;
        }

        public int getId() {
            return id.get();
        }

        public void setId(int value) {
            id.set(value);
        }

        public IntegerProperty idProperty() {
            return id;
        }

        public String getName() {
            return name.get();
        }

        public void setName(String value) {
            name.set(value);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public double getPrice() {
            return price.get();
        }

        public void setPrice(double value) {
            price.set(value);
        }

        public DoubleProperty priceProperty() {
            return price;
        }

        public double getCost() {
            return cost.get();
        }

        public void setCost(double value) {
            cost.set(value);
        }

        public DoubleProperty costProperty() {
            return cost;
        }

        public int getStock() {
            return stock.get();
        }

        public void setStock(int value) {
            stock.set(value);
        }

        public IntegerProperty stockProperty() {
            return stock;
        }

        public boolean isActive() {
            return active.get();
        }

        public void setActive(boolean value) {
            active.set(value);
        }

        public BooleanProperty activeProperty() {
            return active;
        }

        public boolean isPrepared() {
            return prepared.get();
        }

        public void setPrepared(boolean value) {
            prepared.set(value);
        }

        public BooleanProperty preparedProperty() {
            return prepared;
        }

        public Category getCategory() {
            return category.get();
        }

        public void setCategory(Category value) {
            category.set(value);
        }

        public ObjectProperty<Category> categoryProperty() {
            return category;
        }

        public boolean isNew() {
            return isNew;
        }

        public void setNew(boolean value) {
            isNew = value;
        }
    }
}
