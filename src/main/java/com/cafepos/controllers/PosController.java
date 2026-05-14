package com.cafepos.controllers;

import com.cafepos.dao.CategoryDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.TagDAO;
import com.cafepos.dao.TagGroupDAO;
import com.cafepos.hardware.PrinterService;
import com.cafepos.hardware.RFIDHandler;
import com.cafepos.model.Category;
import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.Product;
import com.cafepos.model.Tag;
import com.cafepos.model.TagGroup;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.service.OrderService;
import com.cafepos.service.SessionManager;
import com.cafepos.util.FormatUtils;
import com.cafepos.ui.TagSelectionDialog;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PosController {
    private static final Logger LOG = LoggerFactory.getLogger(PosController.class);

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final OrderService orderService = new OrderService();
    private final PrinterService printerService = new PrinterService();

    private final Order currentOrder = new Order();
    private Customer currentCustomer;
    private int currentCategoryId = -1;

    @FXML
    private Label userLabel;
    @FXML
    private HBox categoryBar;
    @FXML
    private FlowPane productGrid;
    @FXML
    private ListView<OrderLine> orderList;
    @FXML
    private Label totalLabel;
    @FXML
    private TextField rfidBuffer;
    @FXML
    private VBox customerCard;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label customerBalanceLabel;

    public void setUserInfo(String username, String role) {
        String name = username == null ? "" : username;
        String safeRole = role == null ? "" : role;
        if (userLabel != null) {
            userLabel.setText("Connecte: " + name + " (" + safeRole + ")");
        }
    }

    @FXML
    private void initialize() {
        setupRfid();
        loadCategories();
        refreshOrderView();
        configureOrderList();
        Platform.runLater(() -> {
            if (rfidBuffer != null) {
                rfidBuffer.requestFocus();
            }
        });
    }

    private void setupRfid() {
        if (rfidBuffer == null) {
            return;
        }
        RFIDHandler handler = new RFIDHandler(rfidBuffer);
        handler.setOnCard(this::onCardScanned);
    }

    private void onCardScanned(String uid) {
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return customerDAO.findByCardUid(uid);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer customer = task.getValue();
            if (customer == null) {
                showAlert("Carte non reconnue", "Aucun client lie a cette carte.");
                return;
            }
            currentCustomer = customer;
            currentOrder.setCustomer(customer);
            updateCustomerCard();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur lecture carte", task.getException());
            showAlert("Erreur", "Lecture carte impossible.");
        });
        Thread thread = new Thread(task, "rfid-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateCustomerCard() {
        if (currentCustomer == null) {
            customerCard.setVisible(false);
            customerCard.setManaged(false);
            return;
        }
        customerNameLabel.setText(currentCustomer.getName());
        customerBalanceLabel.setText("Solde: " + FormatUtils.formatMoney(currentCustomer.getBalance()));
        customerCard.setVisible(true);
        customerCard.setManaged(true);
    }

    private void loadCategories() {
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                return categoryDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            List<Category> categories = task.getValue();
            if (categories.isEmpty()) {
                showEmptyCategoryHint();
            } else {
                renderCategoryButtons(categories);
                loadProducts(categories.get(0).getId());
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement categories", task.getException());
            showAlert("Erreur", "Impossible de charger les categories.");
        });
        Thread thread = new Thread(task, "category-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderCategoryButtons(List<Category> categories) {
        categoryBar.getChildren().clear();
        for (Category category : categories) {
            Button button = new Button(category.getName());
            button.getStyleClass().add("action-button");
            button.setOnAction(event -> loadProducts(category.getId()));
            categoryBar.getChildren().add(button);
        }
    }

    private void showEmptyCategoryHint() {
        categoryBar.getChildren().clear();
        Label label = new Label("Aucune categorie. Ajoutez des produits.");
        categoryBar.getChildren().add(label);
    }

    private void loadProducts(int categoryId) {
        currentCategoryId = categoryId;
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return productDAO.findActiveByCategory(categoryId);
            }
        };
        task.setOnSucceeded(evt -> renderProducts(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement produits", task.getException());
            showAlert("Erreur", "Impossible de charger les produits.");
        });
        Thread thread = new Thread(task, "product-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderProducts(List<Product> products) {
        productGrid.getChildren().clear();
        if (products == null || products.isEmpty()) {
            Label label = new Label("Aucun produit");
            productGrid.getChildren().add(label);
            return;
        }
        for (Product product : products) {
            String title = product.getName() + "\n" + FormatUtils.formatMoney(product.getPrice());
            if (product.getStock() <= 0) {
                title = title + "\nRupture";
            }
            Button tile = new Button(title);
            tile.getStyleClass().add("product-tile");
            tile.setPrefSize(140, 90);
            if (product.getStock() <= 5 && product.getStock() > 0) {
                tile.getStyleClass().add("low-stock");
            }
            tile.setDisable(product.getStock() <= 0);
            tile.setOnAction(event -> addProduct(product));
            productGrid.getChildren().add(tile);
        }
    }

    private void addProduct(Product product) {
        Task<List<TagGroup>> task = new Task<>() {
            @Override
            protected List<TagGroup> call() throws Exception {
                List<TagGroup> groups = tagGroupDAO.findByProductId(product.getId());
                for (TagGroup group : groups) {
                    List<Tag> tags = tagDAO.findByGroupId(group.getId());
                    for (Tag tag : tags) {
                        group.addTag(tag);
                    }
                }
                return groups;
            }
        };
        task.setOnSucceeded(evt -> {
            List<TagGroup> groups = task.getValue();
            List<Tag> selected;
            if (groups == null || groups.isEmpty()) {
                selected = new ArrayList<>();
            } else {
                selected = TagSelectionDialog.show(
                        productGrid.getScene().getWindow(),
                        product.getName(),
                        groups
                );
            }
            OrderLine line = new OrderLine(product, 1, selected);
            currentOrder.addLine(line);
            refreshOrderView();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement options", task.getException());
            showAlert("Erreur", "Impossible de charger les options.");
        });
        Thread thread = new Thread(task, "tag-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshOrderView() {
        if (orderList == null) {
            return;
        }
        orderList.getItems().setAll(currentOrder.getLines());
        totalLabel.setText("TOTAL: " + FormatUtils.formatMoney(currentOrder.getTotal()));
        updateCustomerCard();
    }

    private void configureOrderList() {
        if (orderList == null) {
            return;
        }
        orderList.setCellFactory(list -> new OrderLineCell());
    }

    @FXML
    private void onDetachCustomer() {
        currentCustomer = null;
        currentOrder.setCustomer(null);
        updateCustomerCard();
    }

    @FXML
    private void onCancelOrder() {
        currentOrder.clear();
        if (currentCustomer != null) {
            currentOrder.setCustomer(currentCustomer);
        }
        refreshOrderView();
    }

    @FXML
    private void onCashPayment() {
        if (currentOrder.getLines().isEmpty()) {
            showAlert("Commande vide", "Ajoutez un produit.");
            return;
        }
        processPayment(PaymentType.ESPECES);
    }

    @FXML
    private void onPrepaidPayment() {
        if (currentOrder.getLines().isEmpty()) {
            showAlert("Commande vide", "Ajoutez un produit.");
            return;
        }
        if (currentCustomer == null) {
            showAlert("Client requis", "Scannez une carte avant le paiement.");
            return;
        }
        processPayment(PaymentType.PREPAYE);
    }

    private void processPayment(PaymentType type) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                orderService.saveOrder(currentOrder, type);
                double remaining = 0;
                if (type == PaymentType.PREPAYE && currentCustomer != null) {
                    remaining = currentCustomer.getBalance() - currentOrder.getTotal();
                }
                printerService.printReceipt(currentOrder, remaining);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            if (type == PaymentType.PREPAYE && currentCustomer != null) {
                double newBalance = currentCustomer.getBalance() - currentOrder.getTotal();
                currentCustomer = new Customer(
                        currentCustomer.getId(),
                        currentCustomer.getName(),
                        currentCustomer.getCardUid(),
                        newBalance
                );
            }
            currentOrder.clear();
            if (currentCustomer != null) {
                currentOrder.setCustomer(currentCustomer);
            }
            refreshOrderView();
            if (currentCategoryId > 0) {
                loadProducts(currentCategoryId);
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur paiement", task.getException());
            showAlert("Erreur", task.getException().getMessage());
        });
        Thread thread = new Thread(task, "payment");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onLogout() {
        // Retour simple a l'ecran de connexion.
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 820, 520);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) userLabel.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec chargement login.fxml", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setHeaderText("Echec retour ecran connexion.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onOpenBackOffice() {
        if (!isManager()) {
            showAlert("Acces refuse", "Fonction reservee au manager.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/backoffice.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) userLabel.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec chargement backoffice.fxml", ex);
            showAlert("Erreur", "Echec ouverture back-office.");
        }
    }

    private boolean isManager() {
        User user = SessionManager.getCurrentUser();
        return user != null && user.getRole() == UserRole.MANAGER;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private class OrderLineCell extends ListCell<OrderLine> {
        private final HBox root = new HBox(8);
        private final VBox textBox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label tagsLabel = new Label();
        private final Label totalLabel = new Label();
        private final Button minusButton = new Button("-");
        private final Button plusButton = new Button("+");

        OrderLineCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            nameLabel.getStyleClass().add("subtitle");
            tagsLabel.getStyleClass().add("hint-label");
            totalLabel.getStyleClass().add("subtitle");
            minusButton.setMinWidth(44);
            plusButton.setMinWidth(44);

            textBox.getChildren().addAll(nameLabel, tagsLabel);
            root.getChildren().addAll(textBox, minusButton, plusButton, totalLabel);
        }

        @Override
        protected void updateItem(OrderLine line, boolean empty) {
            super.updateItem(line, empty);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            nameLabel.setText(line.getProduct().getName() + " x" + line.getQuantity());
            tagsLabel.setText(formatTags(line.getTags()));
            totalLabel.setText(FormatUtils.formatMoney(line.getLineTotal()));

            minusButton.setOnAction(event -> {
                if (line.getQuantity() <= 1) {
                    currentOrder.removeLine(line);
                } else {
                    line.setQuantity(line.getQuantity() - 1);
                }
                refreshOrderView();
            });
            plusButton.setOnAction(event -> {
                line.setQuantity(line.getQuantity() + 1);
                refreshOrderView();
            });
            setGraphic(root);
        }

        private String formatTags(List<Tag> tags) {
            if (tags == null || tags.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Tag tag : tags) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(tag.getName());
            }
            return sb.toString();
        }
    }
}
