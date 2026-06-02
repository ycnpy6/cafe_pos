package com.cafepos.controllers;

import com.cafepos.MainApp;
import com.cafepos.dao.CustomerDAO;
import com.cafepos.dao.CashMovementDAO;
import com.cafepos.dao.CashWithdrawalDAO;
import com.cafepos.dao.ExpenseDAO;
import com.cafepos.dao.OrderDAO;
import com.cafepos.dao.ProductDAO;
import com.cafepos.dao.ProductIngredientDAO;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.dao.TagDAO;
import com.cafepos.dao.TagGroupDAO;
import com.cafepos.dao.UserDAO;
import com.cafepos.dao.WaitingOrderDAO;
import com.cafepos.db.DatabaseManager;
import com.cafepos.model.Category;
import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.PaymentType;
import com.cafepos.model.PosOrderSummary;
import com.cafepos.model.Product;
import com.cafepos.model.ProductIngredientUsage;
import com.cafepos.model.RefundLineSelection;
import com.cafepos.model.RefundableOrderLine;
import com.cafepos.model.Tag;
import com.cafepos.model.TagGroup;
import com.cafepos.model.User;
import com.cafepos.model.UserRole;
import com.cafepos.model.WaitingOrderSummary;
import com.cafepos.service.AccountService;
import com.cafepos.service.OrderService;
import com.cafepos.service.PrintQueueService;
import com.cafepos.service.SessionManager;
import com.cafepos.service.AdminSessionManager;
import com.cafepos.ui.CashTenderDialog;
import com.cafepos.ui.CashWithdrawalDialog;
import com.cafepos.ui.DiscountDialog;
import com.cafepos.ui.ManagerPinDialog;
import com.cafepos.ui.PrepaidPaymentDialog;
import com.cafepos.ui.QuickNewClientDialog;
import com.cafepos.ui.TopupDialog;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.IdleMonitor;
import com.cafepos.util.SecurityUtils;
import com.cafepos.util.ToastService;
import com.cafepos.util.WindowUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
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
import java.util.Locale;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PosController {
    private static final Logger LOG = LoggerFactory.getLogger(PosController.class);
    private static final int MAX_TOASTS = 3;
    private static final String STOCK_THRESHOLD_KEY = "stock.low.threshold";
    private static final String TVA_PERCENT_KEY = "tva_percent";
    private static final String RFID_MODE_KEY = "rfid.mode";
    private static final String RFID_DEVICE_NAME_KEY = "rfid.device.name";
    private static final String RFID_MODE_DISABLED = "DISABLED";
    private static final String PREPAID_SCAN_LABEL = "📡 Scanner la carte…";
    private static final String PREPAID_DEFAULT_LABEL = "Prépayé\nF12";
    private static final String RFID_WAITING_STATUS = "En attente de la carte RFID…";

    private enum PosMode {
        NORMAL,
        SCAN_WAITING
    }

    private enum ScanIntent {
        NONE,
        PREPAID,
        TOPUP
    }

    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final TagGroupDAO tagGroupDAO = new TagGroupDAO();
    private final TagDAO tagDAO = new TagDAO();
    private final UserDAO userDAO = new UserDAO();
    private final CashMovementDAO cashMovementDAO = new CashMovementDAO();
    private final CashWithdrawalDAO cashWithdrawalDAO = new CashWithdrawalDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final ProductIngredientDAO productIngredientDAO = new ProductIngredientDAO();
    private final WaitingOrderDAO waitingOrderDAO = new WaitingOrderDAO();
    private final OrderService orderService = new OrderService();
    private final AccountService accountService = new AccountService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final Order currentOrder = new Order();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<TagControl> tagControls = new ArrayList<>();
    private final Map<Integer, String> categoryColors = new HashMap<>();
    private final Map<Integer, Category> categoriesById = new HashMap<>();
    private final Map<Integer, Boolean> preparedLowStockWarnings = new HashMap<>();

    private Customer currentCustomer;
    private int currentCategoryId = -1;
    private int lowStockThreshold = 5;
    private double tvaPercent;
    private String lastScannedCardUid;
    private boolean lastScannedCardUnknown;
    private OrderLine selectedLine;
    private Product currentTagProduct;
    private long newOrderConfirmAt;
    private PosMode currentMode = PosMode.NORMAL;
    private ScanIntent pendingScanIntent = ScanIntent.NONE;
    private PauseTransition scanTimeout;
    private Timeline prepaidPulseTimeline;
    private boolean rfidCaptureEnabled = true;
    private String prepaidDefaultStyle = "";
    private String prepaidDefaultText = PREPAID_DEFAULT_LABEL;

    @FXML
    private StackPane rootStack;
    @FXML
    private BorderPane posRoot;
    @FXML
    private HBox topBar;
    @FXML
    private HBox statusBar;
    @FXML
    private Label lblSessionInfo;
    @FXML
    private Label lblWorkPeriod;
    @FXML
    private Label lblPrintQueue;
    @FXML
    private VBox categoryBar;
    @FXML
    private VBox categoryTabsBar;
    @FXML
    private VBox categoryTabsContainer;
    @FXML
    private FlowPane productGrid;
    @FXML
    private ScrollPane productScrollPane;
    @FXML
    private VBox tagPanel;
    @FXML
    private VBox supplementPanel;
    @FXML
    private StackPane supplementOverlay;
    @FXML
    private Label tagTitleLabel;
    @FXML
    private Label supplementTitle;
    @FXML
    private VBox tagGroupsBox;
    @FXML
    private VBox supplementGroupsContainer;

    @FXML
    private VBox orderPanel;
    @FXML
    private VBox orderLinesBox;
    @FXML
    private VBox orderLinesContainer;
    @FXML
    private Label emptyOrderLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label lblTotal;
    @FXML
    private HBox discountRow;
    @FXML
    private Label lblDiscount;
    @FXML
    private HBox tvaRow;
    @FXML
    private Label lblTvaTitle;
    @FXML
    private Label lblTva;

    @FXML
    private HBox customerCard;
    @FXML
    private HBox rfidCard;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label rfidName;
    @FXML
    private Label customerBalanceLabel;
    @FXML
    private Label rfidBalance;
    @FXML
    private TextField rfidField;

    @FXML
    private Button btnCash;
    @FXML
    private Button btnPrepaid;
    @FXML
    private Button btnDiscount;
    @FXML
    private Button btnHold;
    @FXML
    private Button btnWaiting;
    @FXML
    private Button btnWithdrawal;

    @FXML
    private VBox cashDialog;
    @FXML
    private VBox cashTenderOverlay;
    @FXML
    private TextField cashInput;
    @FXML
    private Label cashTotalLabel;
    @FXML
    private Label lblCashTotal;
    @FXML
    private Label changeLabel;
    @FXML
    private Label lblChange;

    @FXML
    private VBox splitDialog;
    @FXML
    private VBox splitPaymentOverlay;
    @FXML
    private Label splitBalanceLabel;
    @FXML
    private Label lblSplitCardAmount;
    @FXML
    private Label splitRemainingLabel;
    @FXML
    private Label lblSplitRemaining;
    @FXML
    private TextField splitCashInput;
    @FXML
    private Label splitChangeLabel;
    @FXML
    private Label lblSplitChange;

    @FXML
    private VBox topupDialog;
    @FXML
    private Label topupCustomerLabel;
    @FXML
    private Label topupBalanceLabel;
    @FXML
    private TextField topupAmountInput;
    @FXML
    private Label topupAfterLabel;

    @FXML
    private VBox historyPanel;
    @FXML
    private VBox historyRowsBox;
    @FXML
    private VBox waitingPanel;
    @FXML
    private VBox waitingRowsBox;
    @FXML
    private Label waitingCountBadge;

    @FXML
    private VBox refundDialog;
    @FXML
    private TextField refundSearchField;
    @FXML
    private VBox refundOrdersBox;
    @FXML
    private Label refundSelectedOrderLabel;
    @FXML
    private VBox refundLinesBox;
    @FXML
    private Label refundTotalLabel;
    @FXML
    private CheckBox refundToRfidCheck;
    @FXML
    private TextField refundReasonField;

    @FXML
    private VBox refundPinDialog;
    @FXML
    private TextField refundPinInput;

    @FXML
    private VBox toastContainer;
    @FXML
    private Label printBadge;
    @FXML
    private Label selectedItemNameLabel;
    @FXML
    private Label selectedItemMetaLabel;

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

    private PosOrderSummary selectedRefundOrder;
    private final Map<Integer, RefundableOrderLine> refundLineLookup = new HashMap<>();
    private final Map<Integer, Integer> refundQtyByLineId = new HashMap<>();
    private boolean refundPinGranted;

    public void setUserInfo(String username, String role) {
        // Pas d'affichage direct, mais utile pour l'etat role.
        if (role != null && !role.isBlank()) {
            applyRoleVisibility(UserRole.valueOf(role));
        }
        updateSessionLabels();
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
        if (order.getDiscountPercent() > 0) {
            currentOrder.setDiscountPercent(order.getDiscountPercent());
        } else if (order.getDiscountAmount() > 0) {
            currentOrder.setDiscountAmount(order.getDiscountAmount());
        }
        currentOrder.setTvaPercent(tvaPercent > 0 ? tvaPercent : order.getTvaPercent());
        currentCustomer = order.getCustomer();
        refreshOrderView();
    }

    @FXML
    private void initialize() {
        bindLayoutAliases();
        if (btnPrepaid != null) {
            prepaidDefaultStyle = btnPrepaid.getStyle() == null ? "" : btnPrepaid.getStyle();
            prepaidDefaultText = btnPrepaid.getText() == null || btnPrepaid.getText().isBlank()
                    ? PREPAID_DEFAULT_LABEL
                    : btnPrepaid.getText();
        }
        ToastService.install(rootStack, statusBar);
        loadLowStockThreshold();
        loadTvaPercent();
        setupRfid();
        configureProductGridWrapping();
        loadCategories();
        refreshOrderView();
        updateCustomerCard();
        refreshPrintBadge();
        refreshWaitingBadge();
        applyRoleVisibility(SessionManager.getCurrentUser() == null ? null : SessionManager.getCurrentUser().getRole());
        updateSessionLabels();
        setActiveNav(navCaisse);

        rootStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                configureShortcuts(newScene);
                IdleMonitor.bindScene(newScene);
            }
        });

        if (topupAmountInput != null) {
            topupAmountInput.textProperty().addListener((obs, oldValue, newValue) -> updateTopupAfter());
        }
        if (rootStack != null) {
            rootStack.setOnMouseClicked(event -> {
                if (currentMode == PosMode.SCAN_WAITING && rfidField != null) {
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
        if (refundPinInput != null) {
            refundPinInput.setOnAction(evt -> onRefundPinConfirm());
        }
    }

    private void loadTvaPercent() {
        Task<Double> task = new Task<>() {
            @Override
            protected Double call() throws Exception {
                String value = settingsDAO.getValue(TVA_PERCENT_KEY);
                if (value == null || value.isBlank()) {
                    return 0.0;
                }
                try {
                    return Math.max(0, Double.parseDouble(value.trim().replace(',', '.')));
                } catch (NumberFormatException ignored) {
                    return 0.0;
                }
            }
        };
        task.setOnSucceeded(evt -> {
            tvaPercent = task.getValue();
            currentOrder.setTvaPercent(tvaPercent);
            refreshOrderView();
        });
        Thread thread = new Thread(task, "pos-load-tva");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSessionLabels() {
        if (lblSessionInfo != null) {
            User user = SessionManager.getCurrentUser();
            if (user == null) {
                lblSessionInfo.setText("Session: anonyme");
            } else {
                lblSessionInfo.setText("Session: " + user.getName() + " (" + user.getRole().name() + ")");
            }
        }
        if (lblWorkPeriod != null) {
            Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();
            if (workPeriodId == null || workPeriodId <= 0) {
                lblWorkPeriod.setText("Période de travail inactive");
            } else {
                lblWorkPeriod.setText("Période de travail #" + workPeriodId);
            }
        }
    }

    private void bindLayoutAliases() {
        if (categoryBar == null) {
            categoryBar = categoryTabsBar;
        }
        if (categoryBar == null) {
            categoryBar = categoryTabsContainer;
        }
        if (orderLinesBox == null) {
            orderLinesBox = orderLinesContainer;
        }
        if (totalLabel == null) {
            totalLabel = lblTotal;
        }
        if (customerCard == null) {
            customerCard = rfidCard;
        }
        if (customerNameLabel == null) {
            customerNameLabel = rfidName;
        }
        if (customerBalanceLabel == null) {
            customerBalanceLabel = rfidBalance;
        }
        if (tagPanel == null) {
            tagPanel = supplementPanel;
        }
        if (tagTitleLabel == null) {
            tagTitleLabel = supplementTitle;
        }
        if (tagGroupsBox == null) {
            tagGroupsBox = supplementGroupsContainer;
        }
        if (cashDialog == null) {
            cashDialog = cashTenderOverlay;
        }
        if (cashTotalLabel == null) {
            cashTotalLabel = lblCashTotal;
        }
        if (changeLabel == null) {
            changeLabel = lblChange;
        }
        if (splitDialog == null) {
            splitDialog = splitPaymentOverlay;
        }
        if (splitBalanceLabel == null) {
            splitBalanceLabel = lblSplitCardAmount;
        }
        if (splitRemainingLabel == null) {
            splitRemainingLabel = lblSplitRemaining;
        }
        if (splitChangeLabel == null) {
            splitChangeLabel = lblSplitChange;
        }
    }

    private void configureProductGridWrapping() {
        if (productGrid == null) {
            return;
        }
        if (productScrollPane != null) {
            productGrid.setPrefWrapLength(Math.max(320, productScrollPane.getWidth() - 20));
            productScrollPane.widthProperty().addListener((obs, oldVal, newVal) ->
                    productGrid.setPrefWrapLength(Math.max(320, newVal.doubleValue() - 20)));
        } else {
            productGrid.setPrefWrapLength(700);
        }
    }

    private void applyRoleVisibility(UserRole role) {
        if (navStock != null) {
            navStock.setVisible(true);
            navStock.setManaged(true);
        }
        if (navClients != null) {
            navClients.setVisible(true);
            navClients.setManaged(true);
        }
        if (navRapports != null) {
            navRapports.setVisible(true);
            navRapports.setManaged(true);
        }
        if (navParametres != null) {
            navParametres.setVisible(true);
            navParametres.setManaged(true);
        }
    }

    private void setupRfid() {
        if (rfidField == null) {
            return;
        }

        String rfidMode = null;
        String rfidDeviceName = null;
        try {
            rfidMode = settingsDAO.getValue(RFID_MODE_KEY);
            rfidDeviceName = settingsDAO.getValue(RFID_DEVICE_NAME_KEY);
        } catch (Exception ex) {
            LOG.warn("Lecture parametres RFID impossible", ex);
        }

        if (RFID_MODE_DISABLED.equalsIgnoreCase(rfidMode)) {
            rfidField.setDisable(true);
            rfidField.setPromptText("RFID desactive");
            rfidCaptureEnabled = false;
            return;
        }

        rfidField.setDisable(false);
        rfidCaptureEnabled = true;
        if (rfidDeviceName != null && !rfidDeviceName.isBlank()) {
            rfidField.setPromptText("RFID: " + rfidDeviceName.trim());
        }

        rfidField.setOnAction(event -> {
            String raw = rfidField.getText();
            rfidField.clear();

            if (!rfidCaptureEnabled || currentMode != PosMode.SCAN_WAITING) {
                return;
            }

            String uid = normalizeCardUid(raw);
            if (uid.length() < 6 || uid.length() > 20) {
                return;
            }
            onCardScanned(uid);
        });
    }

    private String normalizeCardUid(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private void enterScanWaitingMode(ScanIntent intent) {
        if (!rfidCaptureEnabled || rfidField == null || rfidField.isDisable()) {
            if (intent == ScanIntent.TOPUP) {
                startQuickClientFlow(lastScannedCardUid);
            } else {
                showToast("warning", "Lecteur RFID indisponible");
            }
            return;
        }

        currentMode = PosMode.SCAN_WAITING;
        pendingScanIntent = intent;

        if (btnPrepaid != null && intent == ScanIntent.PREPAID) {
            btnPrepaid.setText(PREPAID_SCAN_LABEL);
            startPrepaidPulse();
        }

        if (lblPrintQueue != null) {
            lblPrintQueue.setText(RFID_WAITING_STATUS);
            lblPrintQueue.setVisible(true);
            lblPrintQueue.setManaged(true);
        }

        if (scanTimeout == null) {
            scanTimeout = new PauseTransition(Duration.seconds(15));
            scanTimeout.setOnFinished(evt -> {
                if (currentMode == PosMode.SCAN_WAITING) {
                    showToast("info", "Attente carte RFID expirée");
                    exitScanWaitingMode();
                }
            });
        }
        scanTimeout.playFromStart();

        Platform.runLater(() -> {
            rfidField.clear();
            rfidField.requestFocus();
        });
    }

    private void exitScanWaitingMode() {
        currentMode = PosMode.NORMAL;
        pendingScanIntent = ScanIntent.NONE;

        if (scanTimeout != null) {
            scanTimeout.stop();
        }
        stopPrepaidPulse();

        if (btnPrepaid != null) {
            btnPrepaid.setText(prepaidDefaultText);
            btnPrepaid.setStyle(prepaidDefaultStyle);
        }
        refreshPrintBadge();
    }

    private void startPrepaidPulse() {
        if (btnPrepaid == null) {
            return;
        }
        stopPrepaidPulse();
        prepaidPulseTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        e -> btnPrepaid.setStyle(prepaidDefaultStyle
                                + " -fx-border-color: #F59E0B; -fx-border-width: 2px;")),
                new KeyFrame(Duration.millis(450),
                        e -> btnPrepaid.setStyle(prepaidDefaultStyle
                                + " -fx-border-color: #FDBA74; -fx-border-width: 2px;"))
        );
        prepaidPulseTimeline.setAutoReverse(true);
        prepaidPulseTimeline.setCycleCount(Animation.INDEFINITE);
        prepaidPulseTimeline.play();
    }

    private void stopPrepaidPulse() {
        if (prepaidPulseTimeline != null) {
            prepaidPulseTimeline.stop();
            prepaidPulseTimeline = null;
        }
    }

    private void onCardScanned(String uid) {
        lastScannedCardUid = uid == null ? null : uid.trim();
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return customerDAO.findByCardUid(uid);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer customer = task.getValue();
            if (customer == null) {
                lastScannedCardUnknown = true;
                showToast("warning", "Carte non reconnue");
                ScanIntent intent = pendingScanIntent;
                exitScanWaitingMode();
                if (intent == ScanIntent.TOPUP) {
                    startQuickClientFlow(lastScannedCardUid);
                }
                return;
            }
            lastScannedCardUnknown = false;
            currentCustomer = customer;
            currentOrder.setCustomer(customer);
            updateCustomerCard();

            ScanIntent intent = pendingScanIntent;
            exitScanWaitingMode();
            if (intent == ScanIntent.TOPUP) {
                showTopupDialogForCurrentCustomer();
            } else if (intent == ScanIntent.PREPAID) {
                onPrepaidPayment();
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur lecture carte", task.getException());
            showToast("error", "Lecture carte impossible");
            exitScanWaitingMode();
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
                btnPrepaid.setDisable(false);
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

    private void loadLowStockThreshold() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String value = settingsDAO.getValue(STOCK_THRESHOLD_KEY);
                if (value == null || value.isBlank()) {
                    return 5;
                }
                try {
                    int parsed = Integer.parseInt(value.trim());
                    return Math.max(0, parsed);
                } catch (NumberFormatException ignored) {
                    return 5;
                }
            }
        };
        task.setOnSucceeded(evt -> lowStockThreshold = task.getValue());
        Thread thread = new Thread(task, "pos-low-stock-threshold");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadCategories() {
        Task<List<Category>> task = new Task<>() {
            @Override
            protected List<Category> call() throws Exception {
                String sql = """
                        SELECT MIN(id) AS id,
                               name,
                               COALESCE(color, '#6B2D1A') AS color,
                               COALESCE(sort_order, 0) AS sort_order
                        FROM categories
                        WHERE name IS NOT NULL AND TRIM(name) <> ''
                        GROUP BY LOWER(name)
                        ORDER BY sort_order, name
                        """;
                List<Category> categories = new ArrayList<>();
                try (Connection conn = DatabaseManager.openConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        categories.add(new Category(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("color"),
                                rs.getInt("sort_order")
                        ));
                    }
                }
                return categories;
            }
        };
        task.setOnSucceeded(evt -> {
            List<Category> categories = task.getValue();
            categoriesById.clear();
            categoryColors.clear();
            if (categories.isEmpty()) {
                showToast("warning", "Aucune categorie");
                return;
            }
            for (Category category : categories) {
                categoriesById.put(category.getId(), category);
                String color = category.getColor();
                if (color == null || color.isBlank()) {
                    color = defaultCategoryColor(category.getName());
                }
                categoryColors.put(category.getId(), color);
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
        if (categoryBar == null) {
            return;
        }
        categoryBar.getChildren().clear();
        categoryButtons.clear();
        for (Category category : categories) {
            Button button = new Button(shortCategoryLabel(category.getName()));
            button.setWrapText(true);
            button.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            button.setPrefWidth(84);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMaxHeight(Double.MAX_VALUE);
            button.getStyleClass().add("button");
            button.setStyle(categoryTabInactiveStyle());
            button.setUserData(category.getId());
            button.setOnAction(this::onCategorySelected);
            VBox.setVgrow(button, Priority.ALWAYS);
            categoryButtons.add(button);
            categoryBar.getChildren().add(button);
        }
        if (!categoryButtons.isEmpty()) {
            setActiveCategory(categoryButtons.get(0));
        }
    }

    @FXML
    private void onCategorySelected(ActionEvent event) {
        Object source = event.getSource();
        if (!(source instanceof Button button)) {
            return;
        }
        Object rawCategoryId = button.getUserData();
        if (!(rawCategoryId instanceof Integer categoryId)) {
            return;
        }
        loadProducts(categoryId);
        setActiveCategory(button);
        currentCategoryId = categoryId;
    }

    private void setActiveCategory(Button selected) {
        for (Button button : categoryButtons) {
            button.setStyle(categoryTabInactiveStyle());
        }
        if (selected != null) {
            selected.setStyle(categoryTabActiveStyle());
        }
    }

    private String categoryTabInactiveStyle() {
        return "-fx-font-size: 11px;"
                + " -fx-wrap-text: true;"
                + " -fx-text-alignment: center;"
                + " -fx-background-color: #EDE0C4;"
                + " -fx-text-fill: #2C1810;"
                + " -fx-border-color: #D4B896;"
                + " -fx-border-width: 1px;"
                + " -fx-background-radius: 8px;"
                + " -fx-border-radius: 8px;";
    }

    private String categoryTabActiveStyle() {
        return "-fx-font-size: 11px;"
                + " -fx-wrap-text: true;"
                + " -fx-text-alignment: center;"
                + " -fx-background-color: #6B2D1A;"
                + " -fx-text-fill: #F5ECD7;"
                + " -fx-border-color: #6B2D1A;"
                + " -fx-border-width: 1px;"
                + " -fx-background-radius: 8px;"
                + " -fx-border-radius: 8px;";
    }

    private String shortCategoryLabel(String rawName) {
        if (rawName == null) {
            return "";
        }
        String value = rawName.trim();
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 12);
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

    private void loadProducts(int categoryId) {
        currentCategoryId = categoryId;
        Map<Integer, Boolean> warningMap = new HashMap<>();
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                List<Product> products = productDAO.findActiveByCategory(categoryId);
                for (Product product : products) {
                    if (!product.isPrepared()) {
                        continue;
                    }
                    List<ProductIngredientUsage> recipe = productIngredientDAO.findRecipeByProduct(product.getId());
                    if (recipe == null || recipe.isEmpty()) {
                        warningMap.put(product.getId(), false);
                        continue;
                    }
                    boolean lowStock = false;
                    for (ProductIngredientUsage usage : recipe) {
                        if (usage.availableStock() <= lowStockThreshold) {
                            lowStock = true;
                            break;
                        }
                    }
                    warningMap.put(product.getId(), lowStock);
                }
                return products;
            }
        };
        task.setOnSucceeded(evt -> {
            preparedLowStockWarnings.clear();
            preparedLowStockWarnings.putAll(warningMap);
            renderProducts(task.getValue());
        });
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
        products.stream()
                .filter(product -> product.getName() != null && !product.getName().isBlank())
                .filter(Product::isActive)
                .forEach(product -> productGrid.getChildren().add(createProductTile(product)));
    }

    private StackPane createProductTile(Product product) {
        Button tileButton = new Button();
        double wrapLength = productGrid == null ? 700 : productGrid.getPrefWrapLength();
        double tileWidth = Math.max(130, (wrapLength - 40) / 5.0);
        double tileHeight = Math.max(82, tileWidth * 0.65);

        tileButton.setPrefWidth(tileWidth);
        tileButton.setMinWidth(tileWidth);
        tileButton.setMaxWidth(tileWidth);
        tileButton.setPrefHeight(tileHeight);
        tileButton.setMinHeight(tileHeight);
        tileButton.setMaxHeight(tileHeight);
        tileButton.getStyleClass().addAll("button", "elevated");

        Category category = categoriesById.get(product.getCategoryId());
        String categoryColor = category == null ? null : category.getColor();
        if (categoryColor == null || categoryColor.isBlank()) {
            categoryColor = categoryColors.get(product.getCategoryId());
        }
        boolean useDefaultColor = categoryColor == null || categoryColor.isBlank();
        if (useDefaultColor) {
            String rgba = toRgba("#6B2D1A", 0.20);
            String fallback = rgba == null ? "#F0E2CC" : rgba;
            tileButton.setStyle("-fx-background-color: " + fallback + "; -fx-background-radius: 8px;");
        } else {
            tileButton.setStyle("-fx-background-color: " + categoryColor + "; -fx-background-radius: 8px;");
        }

        VBox content = new VBox(3);
        content.setAlignment(javafx.geometry.Pos.CENTER);

        Label name = new Label(product.getName());
        name.setWrapText(true);
        name.setMaxWidth(Math.max(100, tileWidth - 14));
        String textColor = useDefaultColor ? "#2C1810" : "#F5ECD7";
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-alignment: center; -fx-text-fill: " + textColor + ";");
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label price = new Label(formatAmount(product.getPrice()) + " DZD");
        price.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.85;");
        content.getChildren().addAll(name, price);
        tileButton.setGraphic(content);

        PauseTransition longPress = new PauseTransition(Duration.millis(500));
        longPress.setOnFinished(evt -> onProductTileLongPress(product));
        tileButton.setOnMousePressed(evt -> longPress.playFromStart());
        tileButton.setOnMouseReleased(evt -> longPress.stop());
        tileButton.setOnMouseExited(evt -> longPress.stop());
        tileButton.setOnAction(evt -> onProductTileClicked(product));

        StackPane wrapper = new StackPane(tileButton);
        if (shouldShowStockWarning(product)) {
            Label warn = new Label("\u26A0");
            warn.setStyle("-fx-font-size: 14px;");
            warn.getStyleClass().addAll("badge", "warning");
            StackPane.setAlignment(warn, javafx.geometry.Pos.TOP_RIGHT);
            StackPane.setMargin(warn, new Insets(4, 4, 0, 0));
            wrapper.getChildren().add(warn);
        }
        return wrapper;
    }

    private boolean shouldShowStockWarning(Product product) {
        if (product.isPrepared()) {
            return preparedLowStockWarnings.getOrDefault(product.getId(), false);
        }
        return product.getStock() <= lowStockThreshold;
    }

    private void onProductTileClicked(Product product) {
        onProductSelected(product);
    }

    private void onProductTileLongPress(Product product) {
        if (product == null || !isManager()) {
            showToast("warning", "Edition prix reservee manager");
            return;
        }
        requireAdminAccess(() -> {
            TextInputDialog dialog = new TextInputDialog(formatAmount(product.getPrice()));
            dialog.setTitle("Modifier le prix");
            dialog.setHeaderText("Nouveau prix pour " + product.getName());
            dialog.setContentText("Prix DZD:");

            dialog.showAndWait().ifPresent(value -> {
                double newPrice = parseAmount(value);
                if (newPrice < 0) {
                    showToast("warning", "Prix invalide");
                    return;
                }
                Integer userId = SessionManager.getCurrentUser() == null ? null : SessionManager.getCurrentUser().getId();
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        productDAO.updatePriceWithHistory(product.getId(), newPrice, userId);
                        return null;
                    }
                };
                task.setOnSucceeded(evt -> {
                    showToast("success", "Prix mis a jour");
                    if (currentCategoryId > 0) {
                        loadProducts(currentCategoryId);
                    }
                });
                task.setOnFailed(evt -> {
                    LOG.error("Erreur mise a jour prix", task.getException());
                    showToast("error", "Mise a jour prix impossible");
                });
                Thread thread = new Thread(task, "pos-price-update");
                thread.setDaemon(true);
                thread.start();
            });
        });
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

        if (supplementOverlay != null) {
            supplementOverlay.setVisible(true);
            supplementOverlay.setManaged(true);
        }
        tagPanel.setVisible(true);
        tagPanel.setManaged(true);
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
        if (tagPanel != null) {
            tagPanel.setVisible(false);
            tagPanel.setManaged(false);
        }
        if (supplementOverlay != null) {
            supplementOverlay.setVisible(false);
            supplementOverlay.setManaged(false);
        }
        currentTagProduct = null;
    }

    private void addLineToOrder(Product product, List<Tag> selected) {
        OrderLine line = new OrderLine(product, 1, selected);
        currentOrder.addLine(line);
        refreshOrderView();
    }

    private void refreshOrderView() {
        if (orderLinesBox == null) {
            return;
        }
        if (selectedLine != null && !currentOrder.getLines().contains(selectedLine)) {
            selectedLine = null;
        }
        currentOrder.setTvaPercent(tvaPercent);
        orderLinesBox.getChildren().clear();
        if (currentOrder.getLines().isEmpty()) {
            if (emptyOrderLabel != null) {
                emptyOrderLabel.setVisible(true);
                emptyOrderLabel.setManaged(true);
                orderLinesBox.getChildren().add(emptyOrderLabel);
            }
        } else {
            if (emptyOrderLabel != null) {
                emptyOrderLabel.setVisible(false);
                emptyOrderLabel.setManaged(false);
            }
        }

        for (OrderLine line : currentOrder.getLines()) {
            orderLinesBox.getChildren().add(buildOrderLine(line));
        }

        if (discountRow != null && lblDiscount != null) {
            boolean showDiscount = currentOrder.hasDiscount();
            discountRow.setVisible(showDiscount);
            discountRow.setManaged(showDiscount);
            if (showDiscount) {
                lblDiscount.setText("-" + formatMoney(currentOrder.getAppliedDiscountAmount()));
            }
        }

        if (tvaRow != null && lblTva != null) {
            boolean showTva = tvaPercent > 0;
            tvaRow.setVisible(showTva);
            tvaRow.setManaged(showTva);
            if (showTva) {
                if (lblTvaTitle != null) {
                    lblTvaTitle.setText("TVA (" + formatPercent(tvaPercent) + "%):");
                }
                lblTva.setText(formatMoney(currentOrder.getTvaAmount()));
            }
        }

        if (totalLabel != null) {
            totalLabel.setText(formatAmount(currentOrder.getTotal()));
        }
        updateSelectionSummary();
        updateCustomerCard();
    }

    private void updateSelectionSummary() {
        if (selectedItemNameLabel == null || selectedItemMetaLabel == null) {
            return;
        }
        if (selectedLine == null) {
            selectedItemNameLabel.setText("-");
            selectedItemMetaLabel.setText("Touchez un article pour afficher le detail");
            return;
        }
        selectedItemNameLabel.setText(selectedLine.getProduct().getName());
        selectedItemMetaLabel.setText("Qte: " + selectedLine.getQuantity()
            + " • PU: " + formatMoney(selectedLine.getUnitTotal())
                + " • Total: " + formatMoney(selectedLine.getLineTotal()));
    }

    private VBox buildOrderLine(OrderLine line) {
        VBox box = new VBox(4);
        box.getStyleClass().add("order-line");

        HBox header = new HBox(8);
        Label name = new Label(line.getProduct().getName());
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label total = new Label(formatMoney(line.getLineTotal()));
        total.getStyleClass().add("subtitle");
        header.getChildren().addAll(name, total);

        Label tags = new Label(formatTags(line.getTags()));
        tags.getStyleClass().add("hint-label");
        tags.setWrapText(true);
        tags.maxWidthProperty().bind(box.widthProperty().subtract(8));
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
        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        Double tendered = CashTenderDialog.showDialog(owner, currentOrder.getTotal());
        if (tendered == null) {
            return;
        }
        if (tendered + 0.0001 < currentOrder.getTotal()) {
            showToast("warning", "Montant insuffisant");
            return;
        }
        processPayment(PaymentType.ESPECES, currentOrder.getTotal(), 0);
    }

    @FXML
    private void onPrepaidPayment() {
        if (currentOrder.getLines().isEmpty()) {
            showToast("warning", "Ajoutez un produit");
            return;
        }

        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        String suggestedUid = currentCustomer == null ? lastScannedCardUid : currentCustomer.getCardUid();
        PrepaidPaymentDialog.Decision decision = PrepaidPaymentDialog.showDialog(owner, currentOrder.getTotal(), suggestedUid);
        if (decision == null || decision.action() == PrepaidPaymentDialog.Action.CANCEL) {
            return;
        }

        String scannedUid = normalizeCardUid(decision.cardUid());
        if ((scannedUid == null || scannedUid.isBlank()) && currentCustomer == null) {
            showToast("warning", "Scannez une carte RFID");
            return;
        }

        if ((scannedUid == null || scannedUid.isBlank()) && currentCustomer != null) {
            handlePrepaidDecision(currentCustomer, decision.action());
            return;
        }

        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return customerDAO.findByCardUid(scannedUid);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer customer = task.getValue();
            if (customer == null) {
                if (decision.action() == PrepaidPaymentDialog.Action.TOPUP_CARD) {
                    startQuickClientFlow(scannedUid);
                    return;
                }
                showToast("warning", "Carte non reconnue");
                return;
            }
            lastScannedCardUid = scannedUid;
            lastScannedCardUnknown = false;
            handlePrepaidDecision(customer, decision.action());
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur lecture carte prepayee", task.getException());
            showToast("error", "Lecture carte impossible");
        });
        Thread thread = new Thread(task, "prepaid-card-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void handlePrepaidDecision(Customer customer, PrepaidPaymentDialog.Action action) {
        if (customer == null || action == null) {
            return;
        }

        currentCustomer = customer;
        currentOrder.setCustomer(customer);
        updateCustomerCard();

        if (action == PrepaidPaymentDialog.Action.TOPUP_CARD) {
            showTopupDialogForCurrentCustomer();
            return;
        }

        double total = currentOrder.getTotal();
        double balance = currentCustomer.getBalance();

        if (action == PrepaidPaymentDialog.Action.PAY_PREPAID) {
            if (balance + 0.0001 < total) {
                showToast("warning", "Solde insuffisant: utilisez Mixte ou Recharge");
                return;
            }
            currentOrder.setPrepaidAmount(total);
            processPayment(PaymentType.PREPAYE, 0, total);
            return;
        }

        if (action == PrepaidPaymentDialog.Action.MIXED_CASH) {
            double prepaid = Math.max(0, Math.min(balance, total));
            double remaining = total - prepaid;
            if (remaining <= 0.0001) {
                currentOrder.setPrepaidAmount(total);
                processPayment(PaymentType.PREPAYE, 0, total);
                return;
            }
            Stage owner = rootStack == null || rootStack.getScene() == null
                    ? null
                    : (Stage) rootStack.getScene().getWindow();
            Double tendered = CashTenderDialog.showDialog(owner, remaining);
            if (tendered == null) {
                return;
            }
            if (tendered + 0.0001 < remaining) {
                showToast("warning", "Montant insuffisant");
                return;
            }
            processPayment(PaymentType.MIXTE, remaining, prepaid);
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
        hideHistoryPanel();
        onWaitingClose();
        if (refundDialog == null) {
            showToast("info", "Remboursement indisponible");
            return;
        }
        openRefundDialog();
    }

    @FXML
    private void onHistory() {
        hideRefundDialog();
        onWaitingClose();
        if (historyPanel == null) {
            showToast("info", "Historique indisponible");
            return;
        }
        if (historyPanel.isVisible()) {
            onHistoryClose();
            return;
        }
        historyPanel.setVisible(true);
        historyPanel.setManaged(true);
        loadHistoryRows();
    }

    @FXML
    private void onHistoryClose() {
        hideHistoryPanel();
    }

    @FXML
    private void onRefundSearch() {
        loadRefundOrders(refundSearchField == null ? "" : refundSearchField.getText());
    }

    @FXML
    private void onHomeClicked() {
        onLock();
    }

    @FXML
    private void onPayCash() {
        onCashPayment();
    }

    @FXML
    private void onPayPrepaid() {
        onPrepaidPayment();
    }

    @FXML
    private void onWithdrawal() {
        requireAdminAccess(this::openWithdrawalWithSessionAdmin);
    }

    private void openWithdrawalWithSessionAdmin() {
        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        openWithdrawalDialog(owner, SessionManager.getCurrentUser());
    }

    private void openWithdrawalDialog(Stage owner, User manager) {
        Task<Double> cashTask = new Task<>() {
            @Override
            protected Double call() throws Exception {
                return cashMovementDAO.computeExpectedCash(SessionManager.getCurrentWorkPeriodId());
            }
        };
        cashTask.setOnSucceeded(evt -> {
            double expectedCash = cashTask.getValue() == null ? 0 : cashTask.getValue();
            CashWithdrawalDialog.Decision decision = CashWithdrawalDialog.showDialog(owner, expectedCash);
            if (decision == null) {
                return;
            }
            persistWithdrawal(decision, manager);
        });
        cashTask.setOnFailed(evt -> {
            LOG.error("Erreur calcul caisse actuelle", cashTask.getException());
            showToast("error", "Calcul caisse indisponible");
        });
        Thread cashThread = new Thread(cashTask, "withdrawal-expected-cash");
        cashThread.setDaemon(true);
        cashThread.start();
    }

    private void persistWithdrawal(CashWithdrawalDialog.Decision decision, User manager) {
        Integer workPeriodId = SessionManager.getCurrentWorkPeriodId();
        Integer userId = manager == null ? null : manager.getId();
        double amount = decision.amount();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (Connection conn = DatabaseManager.openConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        cashWithdrawalDAO.insert(conn, decision.reason(), amount, userId, workPeriodId);
                        expenseDAO.insert(conn, "WITHDRAWAL", decision.reason(), amount, workPeriodId);
                        cashMovementDAO.insertMovement(
                                conn,
                                CashMovementDAO.TYPE_OUTFLOW,
                                CashMovementDAO.CATEGORY_WITHDRAWAL,
                                amount,
                                decision.reason(),
                                workPeriodId,
                                null,
                                userId
                        );
                        conn.commit();
                    } catch (Exception ex) {
                        conn.rollback();
                        throw ex;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            showToast("success", "💸 Retrait enregistré: -" + formatMoney(amount));
            refreshPrintBadge();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur enregistrement retrait", task.getException());
            showToast("error", "Retrait impossible");
        });
        Thread thread = new Thread(task, "withdrawal-save");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDiscount() {
        if (currentOrder.getLines().isEmpty()) {
            showToast("warning", "Commande vide");
            return;
        }
        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        DiscountDialog.DiscountSelection currentSelection = new DiscountDialog.DiscountSelection(
                currentOrder.getDiscountPercent(),
                currentOrder.getDiscountAmount()
        );
        DiscountDialog.DiscountSelection selection = DiscountDialog.showDialog(owner, currentOrder.getSubtotal(), currentSelection);
        if (selection == null) {
            return;
        }

        if (selection.percent() > 0) {
            currentOrder.setDiscountPercent(selection.percent());
        } else if (selection.amount() > 0) {
            currentOrder.setDiscountAmount(selection.amount());
        } else {
            currentOrder.clearDiscount();
        }
        refreshOrderView();
        showToast("success", "Remise appliquee");
    }

    @FXML
    private void onCancelOrder() {
        onNewOrder();
    }

    @FXML
    private void onReprint() {
        onReprintLast();
    }

    @FXML
    private void onHoldOrder() {
        if (currentOrder.getLines().isEmpty()) {
            showToast("warning", "Commande vide");
            return;
        }
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return waitingOrderDAO.save(currentOrder);
            }
        };
        task.setOnSucceeded(evt -> {
            Integer waitingIdValue = task.getValue();
            int waitingId = waitingIdValue == null ? -1 : waitingIdValue;
            currentOrder.clear();
            currentCustomer = null;
            refreshOrderView();
            if (currentCategoryId > 0) {
                loadProducts(currentCategoryId);
            }
            refreshWaitingBadge();
            showToast("success", waitingId > 0
                    ? "Commande en attente #" + waitingId
                    : "Commande en attente");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur mise en attente", task.getException());
            showToast("error", "Mise en attente impossible");
        });
        Thread thread = new Thread(task, "hold-order");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onWaitingOrders() {
        hideRefundDialog();
        hideHistoryPanel();
        if (waitingPanel == null) {
            showToast("info", "Liste attente indisponible");
            return;
        }
        if (waitingPanel.isVisible()) {
            onWaitingClose();
            return;
        }
        waitingPanel.setVisible(true);
        waitingPanel.setManaged(true);
        loadWaitingRows();
    }

    @FXML
    private void onWaitingClose() {
        if (waitingPanel == null) {
            return;
        }
        waitingPanel.setVisible(false);
        waitingPanel.setManaged(false);
    }

    private void loadWaitingRows() {
        if (waitingRowsBox == null) {
            return;
        }
        waitingRowsBox.getChildren().clear();

        Task<List<WaitingOrderSummary>> task = new Task<>() {
            @Override
            protected List<WaitingOrderSummary> call() throws Exception {
                return waitingOrderDAO.findAll();
            }
        };
        task.setOnSucceeded(evt -> {
            List<WaitingOrderSummary> rows = task.getValue();
            if (rows == null || rows.isEmpty()) {
                Label empty = new Label("Aucune commande en attente");
                empty.getStyleClass().add("text-muted");
                waitingRowsBox.getChildren().add(empty);
                return;
            }
            for (WaitingOrderSummary row : rows) {
                waitingRowsBox.getChildren().add(buildWaitingRow(row));
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement attentes", task.getException());
            Label failed = new Label("Chargement impossible");
            failed.getStyleClass().add("danger");
            waitingRowsBox.getChildren().add(failed);
        });
        Thread thread = new Thread(task, "waiting-orders-load");
        thread.setDaemon(true);
        thread.start();
    }

    private HBox buildWaitingRow(WaitingOrderSummary row) {
        HBox container = new HBox(8);
        container.setStyle("-fx-padding: 8; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        VBox info = new VBox(2);
        Label title = new Label("#" + row.id() + " - " + row.customerName());
        title.getStyleClass().add("text-bold");
        Label details = new Label(row.lineCount() + " ligne(s) • " + formatMoney(row.total()));
        details.getStyleClass().add("text-muted");
        info.getChildren().addAll(title, details);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button resume = new Button("Reprendre");
        resume.getStyleClass().addAll("button", "success");
        resume.setOnAction(evt -> resumeWaitingOrder(row.id()));

        Button delete = new Button("Supprimer");
        delete.getStyleClass().addAll("button", "danger");
        delete.setOnAction(evt -> deleteWaitingOrder(row.id()));

        container.getChildren().addAll(info, resume, delete);
        return container;
    }

    private void resumeWaitingOrder(int waitingOrderId) {
        Task<Order> task = new Task<>() {
            @Override
            protected Order call() throws Exception {
                return waitingOrderDAO.load(waitingOrderId);
            }
        };
        task.setOnSucceeded(evt -> {
            Order waitingOrder = task.getValue();
            if (waitingOrder == null || waitingOrder.getLines().isEmpty()) {
                showToast("warning", "Commande indisponible");
                return;
            }
            restoreOrder(waitingOrder);
            Task<Void> deleteTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    waitingOrderDAO.delete(waitingOrderId);
                    return null;
                }
            };
            deleteTask.setOnSucceeded(done -> {
                refreshWaitingBadge();
                loadWaitingRows();
                showToast("success", "Commande reprise");
            });
            deleteTask.setOnFailed(done -> LOG.error("Erreur suppression attente reprise", deleteTask.getException()));
            Thread deleteThread = new Thread(deleteTask, "waiting-order-delete-after-resume");
            deleteThread.setDaemon(true);
            deleteThread.start();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur reprise attente", task.getException());
            showToast("error", "Reprise impossible");
        });
        Thread thread = new Thread(task, "waiting-order-resume");
        thread.setDaemon(true);
        thread.start();
    }

    private void deleteWaitingOrder(int waitingOrderId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                waitingOrderDAO.delete(waitingOrderId);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            refreshWaitingBadge();
            loadWaitingRows();
            showToast("info", "Commande attente supprimee");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur suppression attente", task.getException());
            showToast("error", "Suppression impossible");
        });
        Thread thread = new Thread(task, "waiting-order-delete");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshWaitingBadge() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return waitingOrderDAO.count();
            }
        };
        task.setOnSucceeded(evt -> {
            Integer countValue = task.getValue();
            int count = countValue == null ? 0 : countValue;
            if (waitingCountBadge == null) {
                return;
            }
            if (count <= 0) {
                waitingCountBadge.setVisible(false);
                waitingCountBadge.setManaged(false);
                return;
            }
            waitingCountBadge.setText(String.valueOf(count));
            waitingCountBadge.setVisible(true);
            waitingCountBadge.setManaged(true);
        });
        task.setOnFailed(evt -> LOG.error("Erreur badge attente", task.getException()));
        Thread thread = new Thread(task, "waiting-orders-count");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onQuickAmount(ActionEvent event) {
        if (cashInput == null) {
            return;
        }
        Object source = event.getSource();
        if (source instanceof Button button) {
            cashInput.setText(button.getText() == null ? "" : button.getText().trim());
            updateCashChange();
        }
    }

    @FXML
    private void onSupplementCancel() {
        onTagCancel();
    }

    @FXML
    private void onSupplementAdd() {
        onTagAdd();
    }

    @FXML
    private void onRefundCancel() {
        hideRefundDialog();
        refundPinGranted = false;
    }

    @FXML
    private void onRefundConfirm() {
        if (selectedRefundOrder == null) {
            showToast("warning", "Selectionnez une commande");
            return;
        }
        List<RefundLineSelection> selections = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : refundQtyByLineId.entrySet()) {
            Integer storedQty = entry.getValue();
            int qty = storedQty == null ? 0 : storedQty.intValue();
            if (qty <= 0) {
                continue;
            }
            RefundableOrderLine line = refundLineLookup.get(entry.getKey());
            if (line == null) {
                continue;
            }
            selections.add(new RefundLineSelection(line.orderLineId(), line.productId(), qty, line.unitPrice()));
        }
        if (selections.isEmpty()) {
            showToast("warning", "Selectionnez au moins une ligne");
            return;
        }
        boolean toRfid = refundToRfidCheck != null && refundToRfidCheck.isSelected();
        if (toRfid && selectedRefundOrder.customerId() == null) {
            showToast("warning", "Commande sans client RFID");
            return;
        }

        String reason = refundReasonField == null ? "" : refundReasonField.getText();
        if (refundDialog != null) {
            refundDialog.setDisable(true);
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                orderService.refundOrder(selectedRefundOrder.orderId(), selections, toRfid, reason);
                return null;
            }
        };
        task.setOnSucceeded(evt -> {
            if (refundDialog != null) {
                refundDialog.setDisable(false);
            }
            showToast("success", "Remboursement enregistre");
            hideRefundDialog();
            refundPinGranted = false;
            if (currentCategoryId > 0) {
                loadProducts(currentCategoryId);
            }
            if (toRfid) {
                refreshCurrentCustomer();
            }
            if (historyPanel != null && historyPanel.isVisible()) {
                loadHistoryRows();
            }
        });
        task.setOnFailed(evt -> {
            if (refundDialog != null) {
                refundDialog.setDisable(false);
            }
            LOG.error("Erreur remboursement", task.getException());
            showToast("error", "Remboursement impossible");
        });
        Thread thread = new Thread(task, "pos-refund");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onRefundPinCancel() {
        if (refundPinDialog != null) {
            refundPinDialog.setVisible(false);
            refundPinDialog.setManaged(false);
        }
        refundPinGranted = false;
        if (rfidField != null) {
            rfidField.requestFocus();
        }
    }

    @FXML
    private void onRefundPinConfirm() {
        String pin = refundPinInput == null ? "" : refundPinInput.getText();
        if (pin == null || pin.isBlank()) {
            showToast("warning", "PIN manager requis");
            return;
        }
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return userDAO.findByPinAndRole(SecurityUtils.sha256Hex(pin.trim()), UserRole.MANAGER);
            }
        };
        task.setOnSucceeded(evt -> {
            User manager = task.getValue();
            if (manager == null) {
                showToast("warning", "PIN manager invalide");
                return;
            }
            if (refundPinDialog != null) {
                refundPinDialog.setVisible(false);
                refundPinDialog.setManaged(false);
            }
            refundPinGranted = true;
            openRefundDialog();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur validation PIN remboursement", task.getException());
            showToast("error", "Verification PIN impossible");
        });
        Thread thread = new Thread(task, "refund-pin-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void openRefundPinDialog() {
        if (refundPinDialog == null) {
            return;
        }
        refundPinGranted = false;
        if (refundPinInput != null) {
            refundPinInput.setText("");
        }
        refundPinDialog.setVisible(true);
        refundPinDialog.setManaged(true);
        Platform.runLater(() -> {
            if (refundPinInput != null) {
                refundPinInput.requestFocus();
            }
        });
    }

    private void openRefundDialog() {
        if (refundDialog == null) {
            return;
        }
        selectedRefundOrder = null;
        refundLineLookup.clear();
        refundQtyByLineId.clear();
        if (refundSearchField != null) {
            refundSearchField.setText("");
        }
        if (refundOrdersBox != null) {
            refundOrdersBox.getChildren().clear();
        }
        if (refundLinesBox != null) {
            refundLinesBox.getChildren().clear();
        }
        if (refundSelectedOrderLabel != null) {
            refundSelectedOrderLabel.setText("Aucune commande selectionnee");
        }
        if (refundTotalLabel != null) {
            refundTotalLabel.setText(formatMoney(0));
        }
        if (refundToRfidCheck != null) {
            refundToRfidCheck.setSelected(false);
        }
        if (refundReasonField != null) {
            refundReasonField.setText("");
        }

        refundDialog.setVisible(true);
        refundDialog.setManaged(true);
        loadRefundOrders("");
        Platform.runLater(() -> {
            if (refundSearchField != null) {
                refundSearchField.requestFocus();
            }
        });
    }

    private void hideRefundDialog() {
        if (refundDialog == null) {
            return;
        }
        refundDialog.setVisible(false);
        refundDialog.setManaged(false);
        refundPinGranted = false;
        selectedRefundOrder = null;
        refundLineLookup.clear();
        refundQtyByLineId.clear();
        if (rfidField != null) {
            rfidField.requestFocus();
        }
    }

    private void hideHistoryPanel() {
        if (historyPanel == null) {
            return;
        }
        historyPanel.setVisible(false);
        historyPanel.setManaged(false);
        if (rfidField != null) {
            rfidField.requestFocus();
        }
    }

    private void loadHistoryRows() {
        Task<List<PosOrderSummary>> task = new Task<>() {
            @Override
            protected List<PosOrderSummary> call() throws Exception {
                return orderDAO.findTodayOrders(20);
            }
        };
        task.setOnSucceeded(evt -> renderHistoryRows(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement historique", task.getException());
            showToast("error", "Historique indisponible");
        });
        Thread thread = new Thread(task, "history-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderHistoryRows(List<PosOrderSummary> rows) {
        if (historyRowsBox == null) {
            return;
        }
        historyRowsBox.getChildren().clear();
        if (rows == null || rows.isEmpty()) {
            historyRowsBox.getChildren().add(new Label("Aucune commande aujourd'hui"));
            return;
        }
        for (PosOrderSummary row : rows) {
            historyRowsBox.getChildren().add(buildHistoryRow(row));
        }
    }

    private HBox buildHistoryRow(PosOrderSummary order) {
        HBox row = new HBox(8);
        row.getStyleClass().add("card");
        Label id = new Label("#" + order.orderId());
        id.getStyleClass().add("subtitle");
        Label date = new Label(FormatUtils.formatDateTime(order.createdAt()));
        date.getStyleClass().add("hint-label");
        Label total = new Label(formatMoney(order.total()));
        total.getStyleClass().add("subtitle");
        Label payment = new Label(order.paymentType().name());
        payment.getStyleClass().add("hint-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button reprint = new Button("Reimprimer");
        reprint.setOnAction(evt -> {
            boolean queued = printQueueService.requeueReceiptForOrder(order.orderId());
            if (queued) {
                showToast("success", "Ticket reenfile");
                refreshPrintBadge();
            } else {
                showToast("warning", "Ticket introuvable");
            }
        });
        row.getChildren().addAll(id, date, total, payment, spacer, reprint);
        row.setOnMouseClicked(evt -> {
            if (refundDialog != null && refundDialog.isVisible()) {
                selectRefundOrder(order);
            }
        });
        return row;
    }

    private void loadRefundOrders(String query) {
        Task<List<PosOrderSummary>> task = new Task<>() {
            @Override
            protected List<PosOrderSummary> call() throws Exception {
                return orderDAO.searchOrders(query, 30);
            }
        };
        task.setOnSucceeded(evt -> renderRefundOrders(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement commandes remboursement", task.getException());
            showToast("error", "Commandes indisponibles");
        });
        Thread thread = new Thread(task, "refund-orders-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderRefundOrders(List<PosOrderSummary> orders) {
        if (refundOrdersBox == null) {
            return;
        }
        refundOrdersBox.getChildren().clear();
        if (orders == null || orders.isEmpty()) {
            refundOrdersBox.getChildren().add(new Label("Aucune commande"));
            return;
        }
        for (PosOrderSummary order : orders) {
            HBox row = new HBox(8);
            row.getStyleClass().add("card");
            if (selectedRefundOrder != null && selectedRefundOrder.orderId() == order.orderId()) {
                row.setStyle("-fx-border-color: -color-accent-emphasis; -fx-border-width: 1px;");
            }
            Label id = new Label("#" + order.orderId());
            id.getStyleClass().add("subtitle");
            Label date = new Label(FormatUtils.formatDateTime(order.createdAt()));
            date.getStyleClass().add("hint-label");
            Label total = new Label(formatMoney(order.total()));
            total.getStyleClass().add("subtitle");
            String customer = order.customerName() == null || order.customerName().isBlank()
                    ? "Sans client"
                    : order.customerName();
            Label customerLabel = new Label(customer);
            customerLabel.getStyleClass().add("hint-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button choose = new Button("Choisir");
            choose.setOnAction(evt -> selectRefundOrder(order));
            row.getChildren().addAll(id, date, total, customerLabel, spacer, choose);
            row.setOnMouseClicked(evt -> selectRefundOrder(order));
            refundOrdersBox.getChildren().add(row);
        }
    }

    private void selectRefundOrder(PosOrderSummary order) {
        selectedRefundOrder = order;
        if (refundSelectedOrderLabel != null) {
            refundSelectedOrderLabel.setText("Commande #" + order.orderId() + " - "
                    + formatMoney(order.total()) + " - "
                    + (order.customerName() == null || order.customerName().isBlank()
                    ? "Sans client" : order.customerName()));
        }
        loadRefundOrders(refundSearchField == null ? "" : refundSearchField.getText());

        Task<List<RefundableOrderLine>> task = new Task<>() {
            @Override
            protected List<RefundableOrderLine> call() throws Exception {
                return orderDAO.findRefundableLines(order.orderId());
            }
        };
        task.setOnSucceeded(evt -> renderRefundLines(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur chargement lignes remboursables", task.getException());
            showToast("error", "Lignes indisponibles");
        });
        Thread thread = new Thread(task, "refund-lines-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderRefundLines(List<RefundableOrderLine> lines) {
        if (refundLinesBox == null) {
            return;
        }
        refundLinesBox.getChildren().clear();
        refundLineLookup.clear();
        refundQtyByLineId.clear();
        if (lines == null || lines.isEmpty()) {
            refundLinesBox.getChildren().add(new Label("Aucune ligne remboursable"));
            updateRefundTotal();
            return;
        }

        for (RefundableOrderLine line : lines) {
            refundLineLookup.put(line.orderLineId(), line);
            refundQtyByLineId.put(line.orderLineId(), 0);

            HBox row = new HBox(8);
            row.getStyleClass().add("card");

            CheckBox include = new CheckBox();
            Label name = new Label(line.productName() + " (restant " + line.refundableQuantity() + ")");
            name.setWrapText(true);
            HBox.setHgrow(name, Priority.ALWAYS);

            Button minus = new Button("-");
            Label qty = new Label("0");
            qty.setMinWidth(24);
            qty.setAlignment(javafx.geometry.Pos.CENTER);
            Button plus = new Button("+");

            Label amount = new Label(formatMoney(0));
            amount.getStyleClass().add("subtitle");

            minus.setDisable(true);
            plus.setDisable(true);

            include.selectedProperty().addListener((obs, oldVal, selected) -> {
                refundQtyByLineId.put(line.orderLineId(), selected ? 1 : 0);
                updateRefundLineState(line, include, qty, amount, minus, plus);
                updateRefundTotal();
            });
            minus.setOnAction(evt -> {
                adjustRefundQty(line, -1, include, qty, amount, minus, plus);
                updateRefundTotal();
            });
            plus.setOnAction(evt -> {
                adjustRefundQty(line, 1, include, qty, amount, minus, plus);
                updateRefundTotal();
            });

            row.getChildren().addAll(include, name, minus, qty, plus, amount);
            refundLinesBox.getChildren().add(row);
        }
        updateRefundTotal();
    }

    private void adjustRefundQty(RefundableOrderLine line, int delta, CheckBox include,
                                 Label qty, Label amount, Button minus, Button plus) {
        int current = refundQtyByLineId.getOrDefault(line.orderLineId(), 0);
        int next = current + delta;
        if (next <= 0) {
            include.setSelected(false);
            refundQtyByLineId.put(line.orderLineId(), 0);
            updateRefundLineState(line, include, qty, amount, minus, plus);
            return;
        }
        int capped = Math.min(next, line.refundableQuantity());
        include.setSelected(true);
        refundQtyByLineId.put(line.orderLineId(), capped);
        updateRefundLineState(line, include, qty, amount, minus, plus);
    }

    private void updateRefundLineState(RefundableOrderLine line, CheckBox include,
                                       Label qty, Label amount, Button minus, Button plus) {
        int value = refundQtyByLineId.getOrDefault(line.orderLineId(), 0);
        if (!include.isSelected()) {
            value = 0;
        }
        if (value > line.refundableQuantity()) {
            value = line.refundableQuantity();
        }
        refundQtyByLineId.put(line.orderLineId(), value);

        qty.setText(String.valueOf(value));
        amount.setText(formatMoney(line.unitPrice() * value));
        minus.setDisable(!include.isSelected() || value <= 1);
        plus.setDisable(!include.isSelected() || value >= line.refundableQuantity());
    }

    private void updateRefundTotal() {
        double total = 0;
        for (Map.Entry<Integer, Integer> entry : refundQtyByLineId.entrySet()) {
            Integer storedQty = entry.getValue();
            int qty = storedQty == null ? 0 : storedQty.intValue();
            if (qty <= 0) {
                continue;
            }
            RefundableOrderLine line = refundLineLookup.get(entry.getKey());
            if (line == null) {
                continue;
            }
            total += line.unitPrice() * qty;
        }
        if (refundTotalLabel != null) {
            refundTotalLabel.setText(formatMoney(total));
        }
    }

    private void refreshCurrentCustomer() {
        if (currentCustomer == null) {
            return;
        }
        int customerId = currentCustomer.getId();
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return customerDAO.findById(customerId);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer refreshed = task.getValue();
            if (refreshed != null) {
                currentCustomer = refreshed;
                currentOrder.setCustomer(refreshed);
                updateCustomerCard();
            }
        });
        Thread thread = new Thread(task, "refresh-customer");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onTopup() {
        if (currentCustomer != null) {
            showTopupDialogForCurrentCustomer();
            return;
        }
        enterScanWaitingMode(ScanIntent.TOPUP);
    }

    private void showTopupDialogForCurrentCustomer() {
        if (currentCustomer == null) {
            return;
        }

        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        Double amount = TopupDialog.showDialog(owner, currentCustomer.getName(), currentCustomer.getBalance());
        if (amount == null) {
            return;
        }
        processTopupAmount(amount);
    }

    private void startQuickClientFlow(String suggestedCardUid) {
        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        QuickNewClientDialog.QuickClientData data = QuickNewClientDialog.showDialog(owner, suggestedCardUid);
        if (data == null) {
            return;
        }

        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                int customerId = customerDAO.insertCustomer(data.name(), data.cardUid(), 0);
                if (customerId <= 0) {
                    return null;
                }
                return customerDAO.findById(customerId);
            }
        };
        task.setOnSucceeded(evt -> {
            Customer created = task.getValue();
            if (created == null) {
                showToast("error", "Creation client impossible");
                return;
            }
            currentCustomer = created;
            currentOrder.setCustomer(created);
            lastScannedCardUid = created.getCardUid();
            lastScannedCardUnknown = false;
            updateCustomerCard();
            showTopupDialogForCurrentCustomer();
            showToast("success", "Client cree, recharge prete");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur creation client rapide", task.getException());
            showToast("error", "Creation client impossible");
        });
        Thread thread = new Thread(task, "quick-client-create");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onTopupQuick(ActionEvent event) {
        String value = getButtonData(event);
        topupAmountInput.setText(value);
        updateTopupAfter();
    }

    @FXML
    private void onTopupCancel() {
        if (topupDialog == null) {
            return;
        }
        topupDialog.setVisible(false);
        topupDialog.setManaged(false);
        if (rfidField != null) {
            rfidField.requestFocus();
        }
    }

    @FXML
    private void onTopupConfirm() {
        if (topupDialog == null || topupAmountInput == null) {
            showToast("warning", "Recharge indisponible");
            return;
        }
        if (currentCustomer == null) {
            onTopupCancel();
            return;
        }
        double amount = parseAmount(topupAmountInput.getText());
        processTopupAmount(amount);
    }

    private void processTopupAmount(double amount) {
        if (amount < 100) {
            showToast("warning", "Minimum 100 DZD");
            return;
        }
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() throws Exception {
                return accountService.topUp(currentCustomer, amount);
            }
        };
        task.setOnSucceeded(evt -> {
            currentCustomer = task.getValue();
            currentOrder.setCustomer(currentCustomer);
            updateCustomerCard();
            if (topupDialog != null && topupDialog.isVisible()) {
                onTopupCancel();
            }
            showToast("success", "Carte rechargee");
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur recharge carte", task.getException());
            showToast("error", "Recharge impossible");
        });
        Thread thread = new Thread(task, "pos-topup");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateTopupAfter() {
        if (topupAfterLabel == null) {
            return;
        }
        if (currentCustomer == null) {
            topupAfterLabel.setText("");
            return;
        }
        double amount = parseAmount(topupAmountInput == null ? "" : topupAmountInput.getText());
        topupAfterLabel.setText("Solde apres: " + formatMoney(currentCustomer.getBalance() + amount));
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
        if (cashDialog == null || cashInput == null || cashTotalLabel == null || changeLabel == null) {
            showToast("warning", "Paiement especes indisponible");
            return;
        }
        cashInput.setText("");
        cashTotalLabel.setText(formatMoney(currentOrder.getTotal()));
        changeLabel.setText("0");
        cashDialog.setVisible(true);
        cashDialog.setManaged(true);
        Platform.runLater(() -> cashInput.requestFocus());
    }

    private void hideCashDialog() {
        if (cashDialog == null) {
            return;
        }
        cashDialog.setVisible(false);
        cashDialog.setManaged(false);
        if (rfidField != null) {
            rfidField.requestFocus();
        }
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
        if (splitDialog == null || splitBalanceLabel == null || splitRemainingLabel == null
                || splitCashInput == null || splitChangeLabel == null) {
            showToast("warning", "Paiement mixte indisponible");
            return;
        }
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
        if (splitDialog == null) {
            return;
        }
        splitDialog.setVisible(false);
        splitDialog.setManaged(false);
        if (rfidField != null) {
            rfidField.requestFocus();
        }
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
            if (printBadge != null) {
                if (count <= 0) {
                    printBadge.setVisible(false);
                    printBadge.setManaged(false);
                } else {
                    printBadge.setText(String.valueOf(count));
                    printBadge.setVisible(true);
                    printBadge.setManaged(true);
                }
            }
            if (lblPrintQueue != null) {
                if (count <= 0) {
                    lblPrintQueue.setVisible(false);
                    lblPrintQueue.setManaged(false);
                } else {
                    lblPrintQueue.setText("🖨 " + count + " ticket(s) en attente");
                    lblPrintQueue.setVisible(true);
                    lblPrintQueue.setManaged(true);
                }
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
        requireAdminAccess(() -> openBackOffice("/com/cafepos/fxml/stock.fxml"));
    }

    @FXML
    private void onNavClients() {
        openBackOffice("/com/cafepos/fxml/clients.fxml");
    }

    @FXML
    private void onNavRapports() {
        requireAdminAccess(() -> openBackOffice("/com/cafepos/fxml/reports.fxml"));
    }

    @FXML
    private void onNavSettings() {
        requireAdminAccess(() -> openBackOffice("/com/cafepos/fxml/settings.fxml"));
    }

    @FXML
    private void onLock() {
        AdminSessionManager.lock();
        SessionManager.setLockedOrder(currentOrder);
        navigateToLaunch();
    }

    private void requireAdminAccess(Runnable action) {
        if (action == null) {
            return;
        }
        if (AdminSessionManager.isAdminUnlocked()) {
            AdminSessionManager.touch();
            action.run();
            return;
        }

        Stage owner = rootStack == null || rootStack.getScene() == null
                ? null
                : (Stage) rootStack.getScene().getWindow();
        String pin = ManagerPinDialog.showDialog(owner);
        if (pin == null || pin.isBlank()) {
            return;
        }

        Task<User> pinTask = new Task<>() {
            @Override
            protected User call() throws Exception {
                return userDAO.findByPinAndRole(SecurityUtils.sha256Hex(pin), UserRole.MANAGER);
            }
        };
        pinTask.setOnSucceeded(evt -> {
            User manager = pinTask.getValue();
            if (manager == null) {
                showToast("warning", "PIN administrateur invalide");
                return;
            }
            AdminSessionManager.unlock();
            action.run();
        });
        pinTask.setOnFailed(evt -> {
            LOG.error("Erreur verification PIN admin", pinTask.getException());
            showToast("error", "Verification PIN impossible");
        });
        Thread pinThread = new Thread(pinTask, "admin-pin-check");
        pinThread.setDaemon(true);
        pinThread.start();
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
        try {
            BackOfficeController.setInitialView(initialView);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/backoffice.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) rootStack.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec ouverture back-office", ex);
            showToast("error", "Back-office indisponible");
        }
    }

    private void navigateToLaunch() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cafepos/fxml/launch.fxml"), MainApp.getMessages());
            Parent root = loader.load();
            Scene scene = new Scene(root, 1100, 700);
            MainApp.applyBrandTheme(scene);
            IdleMonitor.bindScene(scene);
            Stage stage = (Stage) rootStack.getScene().getWindow();
            stage.setScene(scene);
            WindowUtils.applyFullSize(stage);
        } catch (Exception ex) {
            LOG.error("Echec retour launch", ex);
        }
    }

    private void setActiveNav(Button active) {
        List<Button> buttons = new ArrayList<>();
        if (navCaisse != null) {
            buttons.add(navCaisse);
        }
        if (navStock != null) {
            buttons.add(navStock);
        }
        if (navClients != null) {
            buttons.add(navClients);
        }
        if (navRapports != null) {
            buttons.add(navRapports);
        }
        if (navParametres != null) {
            buttons.add(navParametres);
        }
        if (navLock != null) {
            buttons.add(navLock);
        }
        for (Button button : buttons) {
            button.getStyleClass().remove("active");
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
            if (event.getTarget() instanceof TextInputControl) {
                return;
            }
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
                onWithdrawal();
                event.consume();
            } else if (event.getCode() == KeyCode.F5) {
                onNavSettings();
                event.consume();
            } else if (event.getCode() == KeyCode.F6) {
                onNewOrder();
                event.consume();
            } else if (event.getCode() == KeyCode.F7) {
                onDiscount();
                event.consume();
            } else if (event.getCode() == KeyCode.F8) {
                onReprintLast();
                event.consume();
            } else if (event.getCode() == KeyCode.F9) {
                onTopup();
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
                if (refundPinDialog != null && refundPinDialog.isVisible()) {
                    onRefundPinCancel();
                    event.consume();
                } else if (refundDialog != null && refundDialog.isVisible()) {
                    onRefundCancel();
                    event.consume();
                } else if (waitingPanel != null && waitingPanel.isVisible()) {
                    onWaitingClose();
                    event.consume();
                } else if (historyPanel != null && historyPanel.isVisible()) {
                    onHistoryClose();
                    event.consume();
                } else if (cashDialog != null && cashDialog.isVisible()) {
                    hideCashDialog();
                    event.consume();
                } else if (splitDialog != null && splitDialog.isVisible()) {
                    hideSplitDialog();
                    event.consume();
                } else if (topupDialog != null && topupDialog.isVisible()) {
                    onTopupCancel();
                    event.consume();
                } else if (tagPanel != null && tagPanel.isVisible()) {
                    hideTagPanel();
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ENTER) {
                if (refundPinDialog != null && refundPinDialog.isVisible()) {
                    onRefundPinConfirm();
                    event.consume();
                } else if (refundDialog != null && refundDialog.isVisible()) {
                    onRefundConfirm();
                    event.consume();
                } else if (cashDialog != null && cashDialog.isVisible()) {
                    onCashConfirm();
                    event.consume();
                } else if (splitDialog != null && splitDialog.isVisible()) {
                    onSplitConfirm();
                    event.consume();
                } else if (topupDialog != null && topupDialog.isVisible()) {
                    onTopupConfirm();
                    event.consume();
                } else if (tagPanel != null && tagPanel.isVisible()) {
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

    private String formatPercent(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
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
        ToastService.ToastType toastType = switch (type == null ? "" : type.toLowerCase()) {
            case "success" -> ToastService.ToastType.SUCCESS;
            case "warning" -> ToastService.ToastType.WARNING;
            case "error" -> ToastService.ToastType.DANGER;
            default -> ToastService.ToastType.INFO;
        };
        ToastService.show(message, toastType);
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
