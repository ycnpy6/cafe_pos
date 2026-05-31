package com.cafepos.controllers;

import com.cafepos.dao.CategoryDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.ProductTagGroupDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.StockMovementDAO;
import com.cafepos.dao.TagGroupDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Category;
import com.cafepos.model.Product;
import com.cafepos.model.TagGroup;
import com.cafepos.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductsController {
    private static final Logger LOG = LoggerFactory.getLogger(ProductsController.class);
    private static final String PRODUCT_IMAGE_KEY_PREFIX = "product.image.";
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final ProductTagGroupDAO productTagGroupDAO = new ProductTagGroupDAO();
    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final Map<Integer, CheckBox> tagGroupChecks = new HashMap<>();
    private final Map<Integer, String> productImagePaths = new HashMap<>();
    private List<Product> allProducts = new ArrayList<>();

    @FXML
    private TableView<Product> productsTable;
    @FXML
    private TableColumn<Product, String> nameColumn;
    @FXML
    private TableColumn<Product, String> priceColumn;
    @FXML
    private TableColumn<Product, String> stockColumn;
    @FXML
    private TableColumn<Product, String> activeColumn;
    @FXML
    private TextField searchField;
    @FXML
    private Label tableCountLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField priceField;
    @FXML
    private TextField costField;
    @FXML
    private TextField stockField;
    @FXML
    private ComboBox<Category> categoryBox;
    @FXML
    private CheckBox activeBox;
    @FXML
    private Label selectedProductLabel;
    @FXML
    private FlowPane tagGroupBox;
    @FXML
    private TextField stockAdjustField;
    @FXML
    private TextField stockReasonField;
    @FXML
    private TilePane productTilePane;
    @FXML
    private ImageView selectedProductImage;
    @FXML
    private Label selectedImagePlaceholder;

    @FXML
    private void initialize() {
        configureTable();
        configureSearch();
        loadCategories();
        loadProducts();
        loadTagGroups();
        productsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            onProductSelected(newVal);
        });
    }

    private void configureTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        priceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().getPrice())));
        stockColumn.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getStock())));
        activeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isActive() ? "Oui" : "Non"));
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        productsTable.setPlaceholder(new Label("Aucun produit"));
    }

    private void configureSearch() {
        if (searchField == null) {
            return;
        }
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    private void loadCategories() {
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                return categoryDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> categoryBox.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> LOG.error("Erreur categories", task.getException()));
        Thread thread = new Thread(task, "categories-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadProducts() {
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return productDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            allProducts = new ArrayList<>(task.getValue());
            applyFilter();
            loadProductImages(allProducts);
        });
        task.setOnFailed(evt -> LOG.error("Erreur produits", task.getException()));
        Thread thread = new Thread(task, "products-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadTagGroups() {
        Task<List<TagGroup>> task = new Task<>() {
            @Override
            protected List<TagGroup> call() throws Exception {
                return tagGroupDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            tagGroupBox.getChildren().clear();
            tagGroupChecks.clear();
            for (TagGroup group : task.getValue()) {
                CheckBox checkBox = new CheckBox(group.getName());
                checkBox.setUserData(group.getId());
                checkBox.getStyleClass().add("tag-checkbox");
                checkBox.setMinHeight(44);
                tagGroupBox.getChildren().add(checkBox);
                tagGroupChecks.put(group.getId(), checkBox);
            }
        });
        task.setOnFailed(evt -> LOG.error("Erreur groupes options", task.getException()));
        Thread thread = new Thread(task, "taggroup-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void onProductSelected(Product product) {
        if (product == null) {
            selectedProductLabel.setText("Selectionnez un produit");
            clearTagChecks();
            updateSelectedImage(null);
            return;
        }
        selectedProductLabel.setText("Produit: " + product.getName());
        updateSelectedImage(product);
        Task<List<Integer>> task = new Task<>() {
            @Override
            protected List<Integer> call() throws Exception {
                return productTagGroupDAO.findGroupIdsForProduct(product.getId());
            }
        };
        task.setOnSucceeded(evt -> {
            clearTagChecks();
            for (Integer id : task.getValue()) {
                CheckBox check = tagGroupChecks.get(id);
                if (check != null) {
                    check.setSelected(true);
                }
            }
        });
        task.setOnFailed(evt -> LOG.error("Erreur options produit", task.getException()));
        Thread thread = new Thread(task, "product-options");
        thread.setDaemon(true);
        thread.start();
    }

    private void clearTagChecks() {
        for (CheckBox checkBox : tagGroupChecks.values()) {
            checkBox.setSelected(false);
        }
    }

    private void applyFilter() {
        String query = searchField == null ? "" : searchField.getText();
        List<Product> filtered = filterProducts(allProducts, query);
        productsTable.getItems().setAll(filtered);
        updateTableCount(filtered.size());
        renderProductCards(filtered);
    }

    private List<Product> filterProducts(List<Product> products, String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(products);
        }
        String normalized = query.trim().toLowerCase();
        List<Product> filtered = new ArrayList<>();
        for (Product product : products) {
            if (product.getName().toLowerCase().contains(normalized)) {
                filtered.add(product);
            }
        }
        return filtered;
    }

    private void updateTableCount(int count) {
        if (tableCountLabel == null) {
            return;
        }
        tableCountLabel.setText(count + (count > 1 ? " articles" : " article"));
    }

    private void renderProductCards(List<Product> products) {
        if (productTilePane == null) {
            return;
        }
        productTilePane.getChildren().clear();
        if (products.isEmpty()) {
            Label empty = new Label("Aucun produit");
            empty.getStyleClass().add("empty-state");
            productTilePane.getChildren().add(empty);
            return;
        }
        for (Product product : products) {
            productTilePane.getChildren().add(createProductCard(product));
        }
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(6);
        card.getStyleClass().add("product-card");

        StackPane imageFrame = new StackPane();
        imageFrame.getStyleClass().add("image-frame");
        ImageView imageView = new ImageView();
        imageView.setFitWidth(150);
        imageView.setFitHeight(96);
        imageView.setPreserveRatio(true);
        Label placeholder = new Label("Aucune image");
        placeholder.getStyleClass().add("image-placeholder");
        imageFrame.getChildren().addAll(imageView, placeholder);
        setImageForProduct(product, imageView, placeholder, 150, 96);

        Label name = new Label(product.getName());
        name.getStyleClass().add("product-name");
        Label price = new Label(FormatUtils.formatMoney(product.getPrice()));
        price.getStyleClass().add("product-price");

        Label stockLabel = new Label(product.getStock() <= 0 ? "Rupture" : "Stock " + product.getStock());
        stockLabel.getStyleClass().addAll("badge", product.getStock() <= 0 ? "badge-out" : "badge-ok");
        Label activeLabel = new Label(product.isActive() ? "Actif" : "Inactif");
        activeLabel.getStyleClass().addAll("badge", product.isActive() ? "badge-neutral" : "badge-muted");
        HBox meta = new HBox(6, stockLabel, activeLabel);

        card.getChildren().addAll(imageFrame, name, price, meta);
        card.setOnMouseClicked(event -> productsTable.getSelectionModel().select(product));
        return card;
    }

    private void loadProductImages(List<Product> products) {
        Task<Map<Integer, String>> task = new Task<>() {
            @Override
            protected Map<Integer, String> call() throws Exception {
                Map<Integer, String> results = new HashMap<>();
                for (Product product : products) {
                    String value = settingsDAO.getValue(PRODUCT_IMAGE_KEY_PREFIX + product.getId());
                    if (value != null && !value.isBlank()) {
                        results.put(product.getId(), value.trim());
                    }
                }
                return results;
            }
        };
        task.setOnSucceeded(evt -> {
            productImagePaths.clear();
            productImagePaths.putAll(task.getValue());
            applyFilter();
            updateSelectedImage(productsTable.getSelectionModel().getSelectedItem());
        });
        task.setOnFailed(evt -> LOG.error("Erreur images produit", task.getException()));
        Thread thread = new Thread(task, "product-images");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSelectedImage(Product product) {
        if (selectedProductImage == null || selectedImagePlaceholder == null) {
            return;
        }
        if (product == null) {
            selectedProductImage.setImage(null);
            selectedImagePlaceholder.setText("Aucun produit");
            selectedImagePlaceholder.setVisible(true);
            selectedImagePlaceholder.setManaged(true);
            return;
        }
        Image image = loadImage(productImagePaths.get(product.getId()), 240, 140);
        if (image == null) {
            selectedProductImage.setImage(null);
            selectedImagePlaceholder.setText("Aucune image");
            selectedImagePlaceholder.setVisible(true);
            selectedImagePlaceholder.setManaged(true);
            return;
        }
        selectedProductImage.setImage(image);
        selectedImagePlaceholder.setVisible(false);
        selectedImagePlaceholder.setManaged(false);
    }

    private void setImageForProduct(Product product, ImageView imageView, Label placeholder, double width, double height) {
        Image image = loadImage(productImagePaths.get(product.getId()), width, height);
        if (image == null) {
            imageView.setImage(null);
            placeholder.setVisible(true);
            placeholder.setManaged(true);
            return;
        }
        imageView.setImage(image);
        placeholder.setVisible(false);
        placeholder.setManaged(false);
    }

    private Image loadImage(String path, double width, double height) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        return new Image(file.toURI().toString(), width, height, true, true, true);
    }

    @FXML
    private void onAddProduct() {
        Category category = categoryBox.getValue();
        if (category == null) {
            showAlert("Categorie requise", "Selectionnez une categorie.");
            return;
        }
        String name = nameField.getText();
        if (name == null || name.isBlank()) {
            showAlert("Nom requis", "Saisissez un nom de produit.");
            return;
        }
        double price = parseDouble(priceField.getText());
        double cost = parseDouble(costField.getText());
        int stock = (int) parseDouble(stockField.getText());
        boolean active = activeBox.isSelected();

        Product product = new Product(0, name.trim(), price, cost, category.getId(), stock, active);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productDAO.insertProduct(product);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            clearForm();
            loadProducts();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajout produit", task.getException());
            showAlert("Erreur", "Ajout produit impossible.");
        });
        Thread thread = new Thread(task, "product-insert");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSaveProductOptions() {
        Product product = productsTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            showAlert("Produit requis", "Selectionnez un produit.");
            return;
        }
        List<Integer> groupIds = new ArrayList<>();
        for (Map.Entry<Integer, CheckBox> entry : tagGroupChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                groupIds.add(entry.getKey());
            }
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                productTagGroupDAO.setGroupsForProduct(product.getId(), groupIds);
                return null;
            }
        };
        task.setOnSucceeded(evt -> showAlert("OK", "Options enregistrees."));
        task.setOnFailed(evt -> {
            LOG.error("Erreur options produit", task.getException());
            showAlert("Erreur", "Sauvegarde impossible.");
        });
        Thread thread = new Thread(task, "product-options-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onAdjustStock() {
        Product product = productsTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            showAlert("Produit requis", "Selectionnez un produit.");
            return;
        }
        int delta = (int) parseDouble(stockAdjustField.getText());
        if (delta == 0) {
            showAlert("Valeur requise", "Saisissez un ajustement (+/-)." );
            return;
        }
        String reason = stockReasonField.getText();
        if (reason == null || reason.isBlank()) {
            reason = "Ajustement manuel";
        }
        String finalReason = reason;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    productDAO.adjustStock(conn, product.getId(), delta);
                    stockMovementDAO.insertMovement(conn, product.getId(), delta, finalReason);
                    conn.commit();
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            stockAdjustField.clear();
            stockReasonField.clear();
            loadProducts();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur ajustement stock", task.getException());
            showAlert("Erreur", "Ajustement impossible.");
        });
        Thread thread = new Thread(task, "stock-adjust");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onChooseProductImage() {
        Product product = productsTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            showAlert("Produit requis", "Selectionnez un produit.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File file = chooser.showOpenDialog(productsTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        String path = file.getAbsolutePath();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(PRODUCT_IMAGE_KEY_PREFIX + product.getId(), path);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            productImagePaths.put(product.getId(), path);
            applyFilter();
            updateSelectedImage(product);
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur image produit", task.getException());
            showAlert("Erreur", "Impossible d'enregistrer l'image.");
        });
        Thread thread = new Thread(task, "product-image-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onClearProductImage() {
        Product product = productsTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            showAlert("Produit requis", "Selectionnez un produit.");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                settingsDAO.setValue(PRODUCT_IMAGE_KEY_PREFIX + product.getId(), "");
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            productImagePaths.remove(product.getId());
            applyFilter();
            updateSelectedImage(product);
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur suppression image produit", task.getException());
            showAlert("Erreur", "Impossible de supprimer l'image.");
        });
        Thread thread = new Thread(task, "product-image-clear");
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

    private void clearForm() {
        nameField.clear();
        priceField.clear();
        costField.clear();
        stockField.clear();
        activeBox.setSelected(true);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}
