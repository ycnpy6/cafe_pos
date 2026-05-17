package com.cafepos.controllers;

import com.cafepos.dao.CategoryDAO;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.TagDAO;
import com.cafepos.dao.TagGroupDAO;
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
import com.cafepos.service.PrintQueueService;
import com.cafepos.service.SessionManager;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.WindowUtils;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class PosController {
    private static final Logger LOG = LoggerFactory.getLogger(PosController.class);
    private static final int MAX_TOASTS = 3;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final OrderService orderService = new OrderService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final Order currentOrder = new Order();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<TagControl> tagControls = new ArrayList<>();
    private final Map<Integer, String> categoryColors = new HashMap<>();

    private Customer currentCustomer;
    private int currentCategoryId = -1;
    private OrderLine selectedLine;
    private Product currentTagProduct;
    private long newOrderConfirmAt;

    @FXML
    private StackPane rootStack;
    @FXML
    private HBox categoryBar;
    @FXML
    private FlowPane productGrid;
    @FXML
    private VBox tagPanel;
    @FXML
    private Label tagTitleLabel;
    @FXML
    private VBox tagGroupsBox;

    @FXML
    private VBox orderPanel;
    @FXML
    private VBox orderLinesBox;
    @FXML
    private Label emptyOrderLabel;
    @FXML
    private Label totalLabel;

    @FXML
    private HBox customerCard;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label customerBalanceLabel;
    @FXML
    private TextField rfidField;

    @FXML
    private Button btnCash;
    @FXML
    private Button btnPrepaid;

    @FXML
    private VBox cashDialog;
    @FXML
    private TextField cashInput;
    @FXML
    private Label cashTotalLabel;
    @FXML
    private Label changeLabel;

    @FXML
    private VBox splitDialog;
    @FXML
    private Label splitBalanceLabel;
    @FXML
    private Label splitRemainingLabel;
    @FXML
    private TextField splitCashInput;
    @FXML
    private Label splitChangeLabel;

    @FXML
    private VBox toastContainer;
    @FXML
    private Label printBadge;

    @FXML
    private Button navCaisse;
    @FXML
    private Button navStock;
    @FXML
    private Button navClients;
    @FXML
    private Button navRapports;
    @FXML
    private Button navParametres;
    @FXML
    private Button navLock;

    public void setUserInfo(String username, String role) {
        // Pas d'affichage direct, mais utile pour l'etat role.
        if (role != null && !role.isBlank()) {
            applyRoleVisibility(UserRole.valueOf(role));
        }
    }

    public void restoreOrder(Order order) {
        if (order == null) {
            return;
        }
        currentOrder.clear();
        for (OrderLine line : order.getLines()) {
            currentOrder.addLine(new OrderLine(line.getProduct(), line.getQuantity(), line.getTags()));
        }
        currentOrder.setCustomer(order.getCustomer());
        currentCustomer = order.getCustomer();
        refreshOrderView();
    }

    @FXML
    private void initialize() {
        setupRfid();
        loadCategories();
        refreshOrderView();
        updateCustomerCard();
        refreshPrintBadge();
        applyRoleVisibility(SessionManager.getCurrentUser() == null ? null : SessionManager.getCurrentUser().getRole());
        setActiveNav(navCaisse);

        rootStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                configureShortcuts(newScene);
                IdleMonitor.bindScene(newScene);
            }
        });

        Platform.runLater(() -> {
            if (rfidField != null) {
                rfidField.requestFocus();
            }
        });
        if (rootStack != null) {
            rootStack.setOnMouseClicked(event -> {
                if (rfidField != null) {
                    rfidField.requestFocus();
                }
            });
        }
        if (cashInput != null) {
            cashInput.textProperty().addListener((obs, oldVal, newVal) -> updateCashChange());
        }
        if (splitCashInput != null) {
            splitCashInput.textProperty().addListener((obs, oldVal, newVal) -> updateSplitChange());
        }
    }

    private void applyRoleVisibility(UserRole role) {
        boolean manager = role == UserRole.MANAGER;
        if (navStock != null) {
            navStock.setVisible(manager);
            navStock.setManaged(manager);
        }
        if (navClients != null) {
            navClients.setVisible(manager);
            navClients.setManaged(manager);
        }
        if (navRapports != null) {
            navRapports.setVisible(manager);
            navRapports.setManaged(manager);
        }
        if (navParametres != null) {
            navParametres.setVisible(manager);
            navParametres.setManaged(manager);
        }
    }

    private void setupRfid() {
        if (rfidField == null) {
            return;
        }
        RFIDHandler handler = new RFIDHandler(rfidField);
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
                showToast("warning", "Carte non reconnue");
                return;
            }
            currentCustomer = customer;
            currentOrder.setCustomer(customer);
            updateCustomerCard();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur lecture carte", task.getException());
            showToast("error", "Lecture carte impossible");
        });
        Thread thread = new Thread(task, "rfid-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateCustomerCard() {
        if (customerCard == null) {
            return;
        }
        if (currentCustomer == null) {
            customerCard.setVisible(false);
            customerCard.setManaged(false);
            if (btnPrepaid != null) {
                btnPrepaid.setDisable(true);
            }
            return;
        }
        customerNameLabel.setText(currentCustomer.getName());
        customerBalanceLabel.setText(formatMoney(currentCustomer.getBalance()));
        customerCard.setVisible(true);
        customerCard.setManaged(true);
        if (btnPrepaid != null) {
            btnPrepaid.setDisable(false);
        }
        updateCustomerCardBorder();
    }

    private void updateCustomerCardBorder() {
        if (customerCard == null || currentCustomer == null) {
            return;
        }
        double balance = currentCustomer.getBalance();
        double total = currentOrder.getTotal();
        String color;
        if (balance <= 0) {
            color = "-color-danger-emphasis";
        } else if (balance < total) {
            color = "-color-warning-emphasis";
        } else {
            color = "-color-success-emphasis";
        }
        customerCard.setStyle("-fx-border-color: " + color + "; -fx-border-width: 2px;");
    }

    private void loadCategories() {
        Map<Integer, String> colorMap = new HashMap<>();
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                List<Category> categories = categoryDAO.findAll();
                for (Category category : categories) {
                    String value = settingsDAO.getValue("category.color." + category.getId());
                    if (value != null && !value.isBlank()) {
                        colorMap.put(category.getId(), value.trim());
                    }
                }
                return categories;
            }
        };
        task.setOnSucceeded(evt -> {
            List<Category> categories = task.getValue();
            categoryColors.clear();
            categoryColors.putAll(colorMap);
            if (categories.isEmpty()) {
                showToast("warning", "Aucune categorie");
                return;
            }
            renderCategoryButtons(categories);
            loadProducts(categories.get(0).getId());
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement categories", task.getException());
            showToast("error", "Categories indisponibles");
        });
        Thread thread = new Thread(task, "category-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderCategoryButtons(List<Category> categories) {
        categoryBar.getChildren().clear();
        categoryButtons.clear();
        int index = 1;
        for (Category category : categories) {
            Button button = new Button(category.getName());
            button.getStyleClass().add("category-tab");
            int shortcutIndex = index;
            button.setOnAction(event -> {
                loadProducts(category.getId());
                setActiveCategory(button);
                currentCategoryId = category.getId();
            });
            button.setUserData(shortcutIndex);
            categoryButtons.add(button);
            categoryBar.getChildren().add(button);
            index++;
        }
        if (!categoryButtons.isEmpty()) {
            setActiveCategory(categoryButtons.get(0));
        }
    }

    private void setActiveCategory(Button selected) {
        for (Button button : categoryButtons) {
            button.getStyleClass().remove("active");
        }
        if (selected != null) {
            selected.getStyleClass().add("active");
        }
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
            showToast("error", "Produits indisponibles");
        });
        Thread thread = new Thread(task, "product-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderProducts(List<Product> products) {
        productGrid.getChildren().clear();
        if (products == null || products.isEmpty()) {
            Label empty = new Label("Aucun produit");
            empty.getStyleClass().add("empty-state");
            productGrid.getChildren().add(empty);
            return;
        }
        for (Product product : products) {
            productGrid.getChildren().add(createProductTile(product));
        }
    }

    private StackPane createProductTile(Product product) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("product-tile");
        String color = categoryColors.get(product.getCategoryId());
        if (color != null && !color.isBlank()) {
            String rgba = toRgba(color, 0.1);
            if (rgba != null) {
                tile.setStyle("-fx-background-color: " + rgba + ";");
            }
        }

        VBox content = new VBox(4);
        Label name = new Label(product.getName());
        name.setWrapText(true);
        Label price = new Label(formatMoney(product.getPrice()));
        price.getStyleClass().add("hint-label");
        content.getChildren().addAll(name, price);

        tile.getChildren().add(content);

        if (product.getStock() <= 0) {
            tile.getStyleClass().add("out-of-stock");
            Label badge = new Label("Rupture");
            badge.getStyleClass().add("badge-out");
            StackPane.setAlignment(badge, javafx.geometry.Pos.CENTER);
            tile.getChildren().add(badge);
            tile.setDisable(true);
        } else if (product.getStock() <= 5) {
            Label warn = new Label("\u26A0");
            warn.getStyleClass().addAll("badge", "badge-warning");
            StackPane.setAlignment(warn, javafx.geometry.Pos.TOP_RIGHT);
            StackPane.setMargin(warn, new Insets(4, 4, 0, 0));
            tile.getChildren().add(warn);
            tile.getStyleClass().add("low-stock");
        }

        tile.setOnMouseClicked(event -> onProductSelected(product));
        return tile;
    }

    private String toRgba(String hex, double alpha) {
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            int r = Integer.parseInt(value.substring(0, 2), 16);
            int g = Integer.parseInt(value.substring(2, 4), 16);
            int b = Integer.parseInt(value.substring(4, 6), 16);
            return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void onProductSelected(Product product) {
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
            if (groups == null || groups.isEmpty()) {
                addLineToOrder(product, new ArrayList<>());
                return;
            }
            currentTagProduct = product;
            showTagPanel(product.getName(), groups);
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur options produit", task.getException());
            showToast("error", "Options indisponibles");
        });
        Thread thread = new Thread(task, "tag-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void showTagPanel(String productName, List<TagGroup> groups) {
        tagTitleLabel.setText(productName + " — Personnaliser");
        tagGroupsBox.getChildren().clear();
        tagControls.clear();

        for (TagGroup group : groups) {
            VBox groupBox = new VBox(6);
            Label title = new Label(group.getName());
            title.getStyleClass().add("hint-label");
            groupBox.getChildren().add(title);

            if (group.isMultiSelect()) {
                VBox list = new VBox(6);
                for (Tag tag : group.getTags()) {
                    HBox row = new HBox(8);
                    CheckBox checkBox = new CheckBox(tag.getName());
                    Label price = new Label(formatMoney(tag.getPriceModifier()));
                    price.getStyleClass().add("hint-label");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    row.getChildren().addAll(checkBox, spacer, price);
                    list.getChildren().add(row);
                    tagControls.add(new TagControl(tag, checkBox, null));
                }
                groupBox.getChildren().add(list);
            } else {
                FlowPane flow = new FlowPane(8, 8);
                ToggleGroup toggleGroup = new ToggleGroup();
                for (Tag tag : group.getTags()) {
                    ToggleButton toggle = new ToggleButton(tag.getName());
                    toggle.setToggleGroup(toggleGroup);
                    flow.getChildren().add(toggle);
                    tagControls.add(new TagControl(tag, null, toggle));
                }
                groupBox.getChildren().add(flow);
            }
            tagGroupsBox.getChildren().add(groupBox);
        }

        tagPanel.setVisible(true);
        tagPanel.setManaged(true);
        tagPanel.setTranslateX(260);
        TranslateTransition transition = new TranslateTransition(Duration.millis(150), tagPanel);
        transition.setToX(0);
        transition.play();
    }

    @FXML
    private void onTagAdd() {
        if (currentTagProduct == null) {
            hideTagPanel();
            return;
        }
        List<Tag> selected = new ArrayList<>();
        for (TagControl control : tagControls) {
            if (control.checkBox != null && control.checkBox.isSelected()) {
                selected.add(control.tag);
            }
            if (control.toggleButton != null && control.toggleButton.isSelected()) {
                selected.add(control.tag);
            }
        }
        addLineToOrder(currentTagProduct, selected);
        hideTagPanel();
    }

    @FXML
    private void onTagCancel() {
        hideTagPanel();
    }

    private void hideTagPanel() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(150), tagPanel);
        transition.setToX(260);
        transition.setOnFinished(evt -> {
            tagPanel.setVisible(false);
            tagPanel.setManaged(false);
            currentTagProduct = null;
        });
        transition.play();
    }

    private void addLineToOrder(Product product, List<Tag> selected) {
        OrderLine line = new OrderLine(product, 1, selected);
        currentOrder.addLine(line);
        refreshOrderView();
    }

    private void refreshOrderView() {
        orderLinesBox.getChildren().clear();
        if (currentOrder.getLines().isEmpty()) {
            emptyOrderLabel.setVisible(true);
            emptyOrderLabel.setManaged(true);
        } else {
            emptyOrderLabel.setVisible(false);
            emptyOrderLabel.setManaged(false);
        }

        for (OrderLine line : currentOrder.getLines()) {
            orderLinesBox.getChildren().add(buildOrderLine(line));
        }
        totalLabel.setText(formatAmount(currentOrder.getTotal()));
        updateCustomerCard();
    }

    private VBox buildOrderLine(OrderLine line) {
        VBox box = new VBox(4);
        box.getStyleClass().add("order-line");

        HBox header = new HBox(8);
        Label name = new Label(line.getProduct().getName());
        HBox.setHgrow(name, Priority.ALWAYS);
        Label total = new Label(formatMoney(line.getLineTotal()));
        total.getStyleClass().add("subtitle");
        header.getChildren().addAll(name, total);

        Label tags = new Label(formatTags(line.getTags()));
        tags.getStyleClass().add("hint-label");
        boolean hasTags = !tags.getText().isBlank();
        tags.setVisible(hasTags);
        tags.setManaged(hasTags);

        HBox controls = buildQtyControls(line);
        boolean selected = line.equals(selectedLine);
        controls.setVisible(selected);
        controls.setManaged(selected);
        if (selected) {
            box.getStyleClass().add("selected");
        }

        box.getChildren().addAll(header, tags, controls);
        box.setOnMouseClicked(event -> {
            selectedLine = line;
            refreshOrderView();
        });
        return box;
    }

    private HBox buildQtyControls(OrderLine line) {
        HBox controls = new HBox(8);
        Button minus = new Button("-");
        Button plus = new Button("+");
        Button delete = new Button("\uD83D\uDDD1");
        Label qty = new Label(String.valueOf(line.getQuantity()));
        qty.setMinWidth(32);
        qty.setAlignment(javafx.geometry.Pos.CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        minus.setOnAction(event -> changeQuantity(line, -1));
        plus.setOnAction(event -> changeQuantity(line, 1));
        delete.setOnAction(event -> removeLine(line));

        controls.getChildren().addAll(minus, qty, plus, spacer, delete);
        return controls;
    }

    private void changeQuantity(OrderLine line, int delta) {
        int next = line.getQuantity() + delta;
        if (next <= 0) {
            currentOrder.removeLine(line);
        } else {
            line.setQuantity(next);
        }
        refreshOrderView();
    }

    private void removeLine(OrderLine line) {
        currentOrder.removeLine(line);
        refreshOrderView();
    }

    @FXML
    private void onDetachCustomer() {
        currentCustomer = null;
        currentOrder.setCustomer(null);
        updateCustomerCard();
    }

    @FXML
    private void onCashPayment() {
        if (currentOrder.getLines().isEmpty()) {
            showToast("warning", "Ajoutez un produit");
            return;
        }
        showCashDialog();
    }

    @FXML
    private void onPrepaidPayment() {
        if (currentOrder.getLines().isEmpty()) {
            showToast("warning", "Ajoutez un produit");
            return;
        }
        if (currentCustomer == null) {
            showToast("warning", "Scannez une carte");
            return;
        }
        double total = currentOrder.getTotal();
        double balance = currentCustomer.getBalance();
        if (balance >= total) {
            currentOrder.setPrepaidAmount(total);
            processPayment(PaymentType.PREPAYE, 0, total);
        } else {
            showSplitDialog(balance, total - Math.max(0, balance));
        }
    }

    @FXML
    private void onNewOrder() {
        if (currentOrder.getLines().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - newOrderConfirmAt > 3000) {
            newOrderConfirmAt = now;
            showToast("warning", "Appuyez encore pour annuler");
            return;
        }
        currentOrder.clear();
        if (currentCustomer != null) {
            currentOrder.setCustomer(currentCustomer);
        }
        refreshOrderView();
    }

    @FXML
    private void onRefund() {
        showToast("info", "Remboursement bientot disponible");
    }

    @FXML
    private void onHistory() {
        showToast("info", "Historique bientot disponible");
    }

    @FXML
    private void onCashKey(ActionEvent event) {
        String digit = getButtonData(event);
        appendDigit(cashInput, digit);
        updateCashChange();
    }

    @FXML
    private void onCashQuick(ActionEvent event) {
        String value = getButtonData(event);
        cashInput.setText(value);
        updateCashChange();
    }

    @FXML
    private void onCashBackspace() {
        removeLastDigit(cashInput);
        updateCashChange();
    }

    @FXML
    private void onCashConfirm() {
        double total = currentOrder.getTotal();
        double tendered = parseAmount(cashInput.getText());
        if (tendered < total) {
            showToast("warning", "Montant insuffisant");
            return;
        }
        processPayment(PaymentType.ESPECES, total, 0);
    }

    @FXML
    private void onCashCancel() {
        hideCashDialog();
    }

    @FXML
    private void onSplitConfirm() {
        double total = currentOrder.getTotal();
        double balance = currentCustomer == null ? 0 : currentCustomer.getBalance();
        double prepaid = Math.max(0, Math.min(balance, total));
        double remaining = total - prepaid;
        double tendered = parseAmount(splitCashInput.getText());
        if (tendered < remaining) {
            showToast("warning", "Montant insuffisant");
            return;
        }
        processPayment(PaymentType.MIXTE, remaining, prepaid);
    }

    @FXML
    private void onSplitCancel() {
        hideSplitDialog();
    }

    private void showCashDialog() {
        cashInput.setText("");
        cashTotalLabel.setText(formatMoney(currentOrder.getTotal()));
        changeLabel.setText("0");
        cashDialog.setVisible(true);
        cashDialog.setManaged(true);
        Platform.runLater(() -> cashInput.requestFocus());
    }

    private void hideCashDialog() {
        cashDialog.setVisible(false);
        cashDialog.setManaged(false);
        rfidField.requestFocus();
    }

    private void updateCashChange() {
        double total = currentOrder.getTotal();
        double tendered = parseAmount(cashInput.getText());
        double change = Math.max(0, tendered - total);
        changeLabel.setText(formatMoney(change));
    }

    private void updateSplitChange() {
        double total = currentOrder.getTotal();
        double balance = currentCustomer == null ? 0 : currentCustomer.getBalance();
        double prepaid = Math.max(0, Math.min(balance, total));
        double remaining = total - prepaid;
        double tendered = parseAmount(splitCashInput.getText());
        double change = Math.max(0, tendered - remaining);
        splitChangeLabel.setText(formatMoney(change));
    }

    private void showSplitDialog(double balance, double remaining) {
        splitBalanceLabel.setText("Solde carte: " + formatMoney(balance));
        splitRemainingLabel.setText("Reste à payer: " + formatMoney(remaining));
        splitCashInput.setText("");
        splitChangeLabel.setText("0");
        updateSplitChange();
        splitDialog.setVisible(true);
        splitDialog.setManaged(true);
        Platform.runLater(() -> splitCashInput.requestFocus());
    }

    private void hideSplitDialog() {
        splitDialog.setVisible(false);
        splitDialog.setManaged(false);
        rfidField.requestFocus();
    }

    private void processPayment(PaymentType type, double cashAmount, double prepaidAmount) {
        hideCashDialog();
        hideSplitDialog();
        currentOrder.setCashAmount(cashAmount);
        currentOrder.setPrepaidAmount(prepaidAmount);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                orderService.saveOrder(currentOrder, type);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            if (type == PaymentType.PREPAYE && currentCustomer != null) {
                currentCustomer = new Customer(
                        currentCustomer.getId(),
                        currentCustomer.getName(),
                        currentCustomer.getCardUid(),
                        currentCustomer.getBalance() - currentOrder.getTotal()
                );
            } else if (type == PaymentType.MIXTE && currentCustomer != null) {
                currentCustomer = new Customer(
                        currentCustomer.getId(),
                        currentCustomer.getName(),
                        currentCustomer.getCardUid(),
                        currentCustomer.getBalance() - prepaidAmount
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
            printQueueService.dispatchAsync();
            refreshPrintBadge();
            showToast("success", "Paiement enregistre");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur paiement", task.getException());
            showToast("error", "Paiement impossible");
        });
        Thread thread = new Thread(task, "payment");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshPrintBadge() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return printQueueService.countPendingSafe();
            }
        };
        task.setOnSucceeded(evt -> {
            int count = task.getValue();
            if (count <= 0) {
                printBadge.setVisible(false);
                printBadge.setManaged(false);
            } else {
                printBadge.setText(String.valueOf(count));
                printBadge.setVisible(true);
                printBadge.setManaged(true);
            }
        });
        Thread thread = new Thread(task, "print-queue-count");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onNavCaisse() {
        setActiveNav(navCaisse);
    }

    @FXML
    private void onNavStock() {
        openBackOffice("/com/cafepos/fxml/stock.fxml");
    }

    @FXML
    private void onNavClients() {
        openBackOffice("/com/cafepos/fxml/clients.fxml");
    }

    @FXML
    private void onNavRapports() {
        openBackOffice("/com/cafepos/fxml/reports.fxml");
    }

    @FXML
    private void onNavSettings() {
        openBackOffice("/com/cafepos/fxml/settings.fxml");
    }

    @FXML
    private void onLock() {
        SessionManager.setLockedOrder(currentOrder);
        navigateToLogin();
    }

    private void onReprintLast() {
        boolean queued = printQueueService.requeueLastReceipt();
        if (queued) {
            showToast("success", "Ticket reenfile");
        } else {
            showToast("warning", "Aucun ticket a reimprimer");
        }
    }

    private void openBackOffice(String initialView) {
        if (!isManager()) {
            showToast("warning", "Acces reserve au manager");
            return;
        }
        try {
            BackOfficeController.setInitialView(initialView);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/backoffice.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) rootStack.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture back-office", ex);
            showToast("error", "Back-office indisponible");
        }
    }

    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 820, 520);
            scene.getStylesheets().add(getClass().getResource("/com/cafepos/styles/app.css").toExternalForm());
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) rootStack.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec retour login", ex);
        }
    }

    private void setActiveNav(Button active) {
        List<Button> buttons = List.of(navCaisse, navStock, navClients, navRapports, navParametres, navLock);
        for (Button button : buttons) {
            if (button != null) {
                button.getStyleClass().remove("active");
            }
        }
        if (active != null) {
            active.getStyleClass().add("active");
        }
    }

    private boolean isManager() {
        User user = SessionManager.getCurrentUser();
        return user != null && user.getRole() == UserRole.MANAGER;
    }

    private void configureShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F1) {
                onNavCaisse();
                event.consume();
            } else if (event.getCode() == KeyCode.F2) {
                onNavStock();
                event.consume();
            } else if (event.getCode() == KeyCode.F3) {
                onNavClients();
                event.consume();
            } else if (event.getCode() == KeyCode.F4) {
                onNavRapports();
                event.consume();
            } else if (event.getCode() == KeyCode.F5) {
                onNavSettings();
                event.consume();
            } else if (event.getCode() == KeyCode.F6) {
                onNewOrder();
                event.consume();
            } else if (event.getCode() == KeyCode.F8) {
                onReprintLast();
                event.consume();
            } else if (event.getCode() == KeyCode.F10) {
                onRefund();
                event.consume();
            } else if (event.getCode() == KeyCode.F11) {
                onCashPayment();
                event.consume();
            } else if (event.getCode() == KeyCode.F12) {
                onPrepaidPayment();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                if (selectedLine != null) {
                    removeLine(selectedLine);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.ADD || event.getText().equals("+")) {
                if (selectedLine != null) {
                    changeQuantity(selectedLine, 1);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.MINUS || event.getText().equals("-")) {
                if (selectedLine != null) {
                    changeQuantity(selectedLine, -1);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideCashDialog();
                hideSplitDialog();
                hideTagPanel();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                if (cashDialog.isVisible()) {
                    onCashConfirm();
                    event.consume();
                } else if (splitDialog.isVisible()) {
                    onSplitConfirm();
                    event.consume();
                } else if (tagPanel.isVisible()) {
                    onTagAdd();
                    event.consume();
                }
            }
        });
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

    private String formatMoney(double value) {
        return FormatUtils.formatMoney(value);
    }

    private String formatAmount(double value) {
        String formatted = FormatUtils.formatMoney(value);
        return formatted.replace(" DZD", "");
    }

    private void appendDigit(TextField field, String digit) {
        if (field == null || digit == null || digit.isBlank()) {
            return;
        }
        field.setText(field.getText() + digit);
    }

    private void removeLastDigit(TextField field) {
        if (field == null) {
            return;
        }
        String text = field.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        field.setText(text.substring(0, text.length() - 1));
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

    private String getButtonData(ActionEvent event) {
        Object source = event.getSource();
        if (source instanceof Button button && button.getUserData() != null) {
            return String.valueOf(button.getUserData());
        }
        return "";
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
            case "success" -> "✓";
            case "warning" -> "!";
            case "error" -> "×";
            default -> "i";
        };
    }

    private static class TagControl {
        private final Tag tag;
        private final CheckBox checkBox;
        private final ToggleButton toggleButton;

        private TagControl(Tag tag, CheckBox checkBox, ToggleButton toggleButton) {
            this.tag = tag;
            this.checkBox = checkBox;
            this.toggleButton = toggleButton;
        }
    }
}
