package com.cafepos.controllers;

import com.cafepos.model.CashMovementRow;
import com.cafepos.model.IngredientMovementSummaryRow;
import com.cafepos.model.IngredientUsageRow;
import com.cafepos.model.OrderHistoryRow;
import com.cafepos.model.OrderLineExportRow;
import com.cafepos.model.OrderLineDetail;
import com.cafepos.model.PaymentType;
import com.cafepos.model.PrintTicketType;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.SessionRow;
import com.cafepos.model.TopItem;
import com.cafepos.service.PrintQueueService;
import com.cafepos.service.ReportService;
import com.cafepos.dao.SettingsDAO;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.UiIconHelper;
import com.cafepos.ui.PrintTicketDialog;
import javafx.collections.FXCollections;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ReportsController {
    private static final Logger LOG = LoggerFactory.getLogger(ReportsController.class);
    private static final int MAX_TOASTS = 3;
    private static final int TOP_LIMIT = 10;
    private static final int EXPORT_LIMIT = 9999;
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String EXPORT_DIR_KEY = "export.default.dir";

    private final ReportService reportService = new ReportService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final ObservableList<OrderHistoryRow> historyMaster = FXCollections.observableArrayList();
    private final FilteredList<OrderHistoryRow> historyFiltered = new FilteredList<>(historyMaster, row -> true);
    private final ObservableList<SessionRow> sessionMaster = FXCollections.observableArrayList();
    private final ObservableList<CashMovementRow> expenseMaster = FXCollections.observableArrayList();

    private LocalDate rangeStart = LocalDate.now();
    private LocalDate rangeEnd = LocalDate.now();

    @FXML
    private ToggleButton todayButton;
    @FXML
    private ToggleButton yesterdayButton;
    @FXML
    private ToggleButton weekButton;
    @FXML
    private ToggleButton monthButton;
    @FXML
    private ToggleButton customButton;
    @FXML
    private HBox customRangeBox;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;

    @FXML
    private Label kpiTotalLabel;
    @FXML
    private Label kpiOrdersLabel;
    @FXML
    private Label kpiCashLabel;
    @FXML
    private Label kpiPrepaidLabel;
    @FXML
    private Label kpiIngredientCostLabel;
    @FXML
    private Label kpiGrossProfitLabel;
    @FXML
    private Label kpiWithdrawalsLabel;
    @FXML
    private Label kpiNetRevenueLabel;

    @FXML
    private VBox topItemsBox;
    @FXML
    private BarChart<String, Number> dailySalesChart;
    @FXML
    private PieChart paymentMixChart;

    @FXML
    private TextField historySearchField;
    @FXML
    private ComboBox<String> paymentFilter;
    @FXML
    private ComboBox<String> userFilter;

    @FXML
    private TableView<OrderHistoryRow> historyTable;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyIdColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyDateColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyItemsColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyTotalColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyCostColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyGrossColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyPaymentColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyClientColumn;
    @FXML
    private TableColumn<OrderHistoryRow, String> historyUserColumn;

    @FXML
    private VBox orderDetailPane;
    @FXML
    private Label orderDetailTitle;
    @FXML
    private VBox orderLinesBox;

    @FXML
    private TableView<SessionRow> sessionsTable;
    @FXML
    private TableColumn<SessionRow, String> sessionIdColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionOpenColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionCloseColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionOrdersColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionTotalColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionCashColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionPrepaidColumn;
    @FXML
    private TableColumn<SessionRow, String> sessionModeColumn;
    @FXML
    private Label sessionsSummaryLabel;

    @FXML
    private TableView<CashMovementRow> expensesTable;
    @FXML
    private TableColumn<CashMovementRow, String> expenseDateColumn;
    @FXML
    private TableColumn<CashMovementRow, String> expenseTypeColumn;
    @FXML
    private TableColumn<CashMovementRow, String> expenseCategoryColumn;
    @FXML
    private TableColumn<CashMovementRow, String> expenseAmountColumn;
    @FXML
    private TableColumn<CashMovementRow, String> expenseUserColumn;
    @FXML
    private TableColumn<CashMovementRow, String> expenseNoteColumn;
    @FXML
    private Label expensesSummaryLabel;


    @FXML
    private VBox toastContainer;

    @FXML
    private void initialize() {
        configurePeriodButtons();
        configureHistoryTable();
        configureSessionsTable();
        configureExpensesTable();
        configureFilters();
        refreshAll();
    }

    private void configurePeriodButtons() {
        ToggleGroup group = new ToggleGroup();
        todayButton.setToggleGroup(group);
        yesterdayButton.setToggleGroup(group);
        weekButton.setToggleGroup(group);
        monthButton.setToggleGroup(group);
        customButton.setToggleGroup(group);
        todayButton.setSelected(true);

        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today);
        endDatePicker.setValue(today);

        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected instanceof ToggleButton toggle) {
                applyRangeFromToggle(toggle);
            }
        });

        startDatePicker.valueProperty().addListener((obs, old, value) -> {
            if (customButton.isSelected()) {
                applyCustomRange();
            }
        });
        endDatePicker.valueProperty().addListener((obs, old, value) -> {
            if (customButton.isSelected()) {
                applyCustomRange();
            }
        });
    }

    private void applyRangeFromToggle(ToggleButton selected) {
        if (selected == customButton) {
            customRangeBox.setManaged(true);
            customRangeBox.setVisible(true);
            applyCustomRange();
            return;
        }
        customRangeBox.setManaged(false);
        customRangeBox.setVisible(false);

        LocalDate today = LocalDate.now();
        if (selected == todayButton) {
            rangeStart = today;
            rangeEnd = today;
        } else if (selected == yesterdayButton) {
            rangeStart = today.minusDays(1);
            rangeEnd = today.minusDays(1);
        } else if (selected == weekButton) {
            rangeStart = today.minusDays(6);
            rangeEnd = today;
        } else if (selected == monthButton) {
            rangeStart = today.withDayOfMonth(1);
            rangeEnd = today;
        }
        refreshAll();
    }

    private void applyCustomRange() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) {
            return;
        }
        if (end.isBefore(start)) {
            end = start;
            endDatePicker.setValue(end);
        }
        rangeStart = start;
        rangeEnd = end;
        refreshAll();
    }

    private void configureHistoryTable() {
        historyIdColumn.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().orderId()));
        historyDateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatDateTime(data.getValue().createdAt())));
        historyItemsColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().itemCount())));
        historyTotalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().total())));
        if (historyCostColumn != null) {
            historyCostColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().ingredientCost())));
        }
        if (historyGrossColumn != null) {
            historyGrossColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().grossProfit())));
        }
        historyPaymentColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().paymentType().name()));
        if (historyClientColumn != null) {
            historyClientColumn.setCellValueFactory(data -> new SimpleStringProperty(
                    formatClientLabel(data.getValue())));
            historyClientColumn.setCellFactory(column -> new TableCell<>() {
                private final Hyperlink link = new Hyperlink();

                {
                    link.setOnAction(evt -> {
                        OrderHistoryRow row = getTableRow() == null ? null : (OrderHistoryRow) getTableRow().getItem();
                        if (row != null && row.clientId() != null) {
                            filterByClient(row);
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isBlank() || "Sans client".equals(item)) {
                        setGraphic(null);
                        setText(item == null ? "" : item);
                        return;
                    }
                    link.setText(item);
                    setText(null);
                    setGraphic(link);
                }
            });
        }
        historyUserColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().userName()));
        historyTable.setItems(historyFiltered);
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                orderDetailPane.setVisible(false);
                orderDetailPane.setManaged(false);
            } else {
                showOrderDetails(selected);
            }
        });
    }

    private void configureSessionsTable() {
        sessionIdColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().sessionId())));
        sessionOpenColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatDateTime(data.getValue().openedAt())));
        sessionCloseColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatDateTime(data.getValue().closedAt())));
        sessionOrdersColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().orderCount())));
        sessionTotalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().total())));
        sessionCashColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().cashTotal())));
        sessionPrepaidColumn.setCellValueFactory(data -> new SimpleStringProperty(
                FormatUtils.formatMoney(data.getValue().prepaidTotal())));
        sessionModeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().closeMode()));
        sessionsTable.setItems(sessionMaster);
    }

        private void configureExpensesTable() {
        if (expensesTable == null) {
            return;
        }
        expenseDateColumn.setCellValueFactory(data -> new SimpleStringProperty(
            FormatUtils.formatDateTime(data.getValue().createdAt())));
        expenseTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(
            normalizeMovementType(data.getValue().movementType())));
        expenseCategoryColumn.setCellValueFactory(data -> new SimpleStringProperty(
            formatExpenseCategory(data.getValue().category())));
        expenseAmountColumn.setCellValueFactory(data -> new SimpleStringProperty(
            FormatUtils.formatMoney(data.getValue().amount())));
        expenseUserColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().userName()));
        expenseNoteColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        expensesTable.setItems(expenseMaster);
        }

    private void configureFilters() {
        paymentFilter.getItems().setAll("Tous", PaymentType.ESPECES.name(), PaymentType.PREPAYE.name(), PaymentType.MIXTE.name());
        paymentFilter.setValue("Tous");
        userFilter.getItems().setAll("Tous");
        userFilter.setValue("Tous");

        historySearchField.textProperty().addListener((obs, old, value) -> applyFilters());
        paymentFilter.valueProperty().addListener((obs, old, value) -> applyFilters());
        userFilter.valueProperty().addListener((obs, old, value) -> applyFilters());
    }

    private void applyFilters() {
        String query = safeLower(historySearchField.getText());
        String payment = paymentFilter.getValue();
        String user = userFilter.getValue();
        historyFiltered.setPredicate(row -> {
            boolean matchQuery = query.isBlank()
                    || String.valueOf(row.orderId()).contains(query)
                    || (row.clientId() != null && String.valueOf(row.clientId()).contains(query))
                    || safeLower(row.clientName()).contains(query)
                    || safeLower(row.userName()).contains(query);
            boolean matchPayment = payment == null || payment.equals("Tous") || row.paymentType().name().equals(payment);
            boolean matchUser = user == null || user.equals("Tous") || row.userName().equalsIgnoreCase(user);
            return matchQuery && matchPayment && matchUser;
        });
    }

    private void refreshAll() {
        LocalDate start = rangeStart;
        LocalDate end = rangeEnd;
        Task<ReportBundle> task = new Task<>() {
            @Override
            protected ReportBundle call() throws Exception {
                SalesSummary summary = reportService.getSummary(start, end);
                List<TopItem> topItems = reportService.getTopItems(start, end, TOP_LIMIT);
                List<OrderHistoryRow> history = reportService.getOrderHistory(start, end);
                List<SessionRow> sessions = reportService.getSessions(start, end);
                List<CashMovementRow> expenses = reportService.getCashMovements(start, end);
                return new ReportBundle(summary, topItems, history, sessions, expenses);
            }
        };
        task.setOnSucceeded(evt -> {
            ReportBundle bundle = task.getValue();
            updateKpis(bundle.summary());
            renderTopItems(bundle.topItems());
            renderCharts(bundle.history());
            historyMaster.setAll(bundle.history());
            updateUserFilter(bundle.history());
            sessionMaster.setAll(bundle.sessions());
            updateSessionSummary();
            expenseMaster.setAll(bundle.expenses());
            updateExpenseSummary();
            applyFilters();
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur rapports", task.getException());
            showToast("error", "Chargement rapports impossible");
        });
        Thread thread = new Thread(task, "reports-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderCharts(List<OrderHistoryRow> historyRows) {
        if (historyRows == null) {
            historyRows = List.of();
        }
        renderDailySalesChart(historyRows);
        renderPaymentMixChart(historyRows);
    }

    private void renderDailySalesChart(List<OrderHistoryRow> historyRows) {
        if (dailySalesChart == null) {
            return;
        }
        Map<String, Double> byDay = new LinkedHashMap<>();
        LocalDate cursor = rangeStart;
        while (!cursor.isAfter(rangeEnd)) {
            byDay.put(cursor.toString(), 0.0);
            cursor = cursor.plusDays(1);
        }
        for (OrderHistoryRow row : historyRows) {
            if (row.createdAt() == null || row.createdAt().length() < 10) {
                continue;
            }
            String day = row.createdAt().substring(0, 10);
            byDay.computeIfPresent(day, (k, v) -> v + row.total());
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> entry : byDay.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        dailySalesChart.getData().setAll(series);
    }

    private void renderPaymentMixChart(List<OrderHistoryRow> historyRows) {
        if (paymentMixChart == null) {
            return;
        }
        double cash = 0;
        double prepaid = 0;
        double mixed = 0;
        for (OrderHistoryRow row : historyRows) {
            if (row.paymentType() == PaymentType.ESPECES) {
                cash += row.total();
            } else if (row.paymentType() == PaymentType.PREPAYE) {
                prepaid += row.total();
            } else if (row.paymentType() == PaymentType.MIXTE) {
                mixed += row.total();
            }
        }

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList(
                new PieChart.Data("Especes", cash),
                new PieChart.Data("Prepayes", prepaid),
                new PieChart.Data("Mixte", mixed)
        );
        paymentMixChart.setData(data);
    }

    private void updateKpis(SalesSummary summary) {
        kpiTotalLabel.setText(FormatUtils.formatMoney(summary.total()));
        kpiOrdersLabel.setText(String.valueOf(summary.orderCount()));
        kpiCashLabel.setText(FormatUtils.formatMoney(summary.cashTotal()));
        kpiPrepaidLabel.setText(FormatUtils.formatMoney(summary.prepaidTotal()));
        if (kpiIngredientCostLabel != null) {
            kpiIngredientCostLabel.setText(FormatUtils.formatMoney(summary.ingredientCost()));
        }
        if (kpiGrossProfitLabel != null) {
            kpiGrossProfitLabel.setText(FormatUtils.formatMoney(summary.grossProfit()));
        }
        if (kpiWithdrawalsLabel != null) {
            kpiWithdrawalsLabel.setText(FormatUtils.formatMoney(summary.cashWithdrawals()));
        }
        if (kpiNetRevenueLabel != null) {
            kpiNetRevenueLabel.setText(FormatUtils.formatMoney(summary.netRevenue()));
        }
    }

    private void renderTopItems(List<TopItem> items) {
        topItemsBox.getChildren().clear();
        if (items == null || items.isEmpty()) {
            Label empty = new Label("Aucune vente sur cette periode");
            empty.getStyleClass().add("empty-state");
            topItemsBox.getChildren().add(empty);
            return;
        }
        int maxQty = items.stream().mapToInt(TopItem::quantity).max().orElse(1);
        int rank = 1;
        for (TopItem item : items) {
            HBox row = new HBox(10);
            row.getStyleClass().add("top-item-row");
            Label rankLabel = new Label(String.valueOf(rank));
            rankLabel.getStyleClass().addAll("badge", "badge-muted");
            Label nameLabel = new Label(item.name());
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            HBox barBox = new HBox();
            barBox.getStyleClass().add("top-item-track");
            barBox.setMinHeight(8);
            barBox.setPrefHeight(8);
            barBox.setMaxHeight(8);
            Region bar = new Region();
            bar.getStyleClass().add("top-item-bar");
            bar.setMinHeight(8);
            bar.setPrefHeight(8);
            bar.setMaxHeight(8);
            double ratio = item.quantity() / (double) maxQty;
            bar.setPrefWidth(Math.max(12, 160 * ratio));
            barBox.getChildren().add(bar);
            HBox.setHgrow(barBox, Priority.ALWAYS);

            Label qtyLabel = new Label(item.quantity() + "x");
            Label revenueLabel = new Label(FormatUtils.formatMoney(item.revenue()));
            row.getChildren().addAll(rankLabel, nameLabel, barBox, qtyLabel, revenueLabel);
            topItemsBox.getChildren().add(row);
            rank++;
        }
    }

    private void updateUserFilter(List<OrderHistoryRow> rows) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (OrderHistoryRow row : rows) {
            if (row.userName() != null && !row.userName().isBlank()) {
                names.add(row.userName());
            }
        }
        String current = userFilter.getValue();
        List<String> options = new ArrayList<>();
        options.add("Tous");
        options.addAll(names);
        userFilter.getItems().setAll(options);
        if (current != null && options.contains(current)) {
            userFilter.setValue(current);
        } else {
            userFilter.setValue("Tous");
        }
    }

    private void updateSessionSummary() {
        double total = sessionMaster.stream().mapToDouble(SessionRow::total).sum();
        int orders = sessionMaster.stream().mapToInt(SessionRow::orderCount).sum();
        sessionsSummaryLabel.setText("Total: " + FormatUtils.formatMoney(total) + " / Commandes: " + orders);
    }

    private void updateExpenseSummary() {
        if (expensesSummaryLabel == null) {
            return;
        }
        double totalOutflow = expenseMaster.stream()
                .filter(row -> "OUTFLOW".equalsIgnoreCase(row.movementType()))
                .mapToDouble(CashMovementRow::amount)
                .sum();
        int count = expenseMaster.size();
        expensesSummaryLabel.setText("Sorties: " + FormatUtils.formatMoney(totalOutflow)
                + " / Mouvements: " + count);
    }

    private String normalizeMovementType(String movementType) {
        if (movementType == null) {
            return "";
        }
        return switch (movementType.toUpperCase()) {
            case "OUTFLOW" -> "Sortie";
            case "INFLOW" -> "Entree";
            default -> movementType;
        };
    }

    private String formatExpenseCategory(String category) {
        if (category == null) {
            return "";
        }
        return switch (category.toUpperCase()) {
            case "INGREDIENT_PURCHASE" -> "Achat ingredients";
            case "SHOPPING" -> "Shopping";
            case "OTHER" -> "Autre";
            default -> category;
        };
    }

    private void showOrderDetails(OrderHistoryRow row) {
        orderDetailPane.setManaged(true);
        orderDetailPane.setVisible(true);
        orderDetailTitle.setText("Commande #" + row.orderId() + " - "
            + FormatUtils.formatDateTime(row.createdAt()) + " - "
            + FormatUtils.formatMoney(row.total()));
        orderLinesBox.getChildren().clear();
        Task<List<OrderLineDetail>> task = new Task<>() {
            @Override
            protected List<OrderLineDetail> call() throws Exception {
                return reportService.getOrderDetails(row.orderId());
            }
        };
        task.setOnSucceeded(evt -> renderOrderDetails(task.getValue()));
        task.setOnFailed(evt -> {
            LOG.error("Erreur details commande", task.getException());
            showToast("error", "Details indisponibles");
        });
        Thread thread = new Thread(task, "order-details");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderOrderDetails(List<OrderLineDetail> details) {
        orderLinesBox.getChildren().clear();
        if (details == null || details.isEmpty()) {
            Label empty = new Label("Aucune ligne");
            empty.getStyleClass().add("empty-state");
            orderLinesBox.getChildren().add(empty);
            return;
        }
        for (OrderLineDetail detail : details) {
            HBox row = new HBox(10);
            row.getStyleClass().add("detail-row");
            Label name = new Label(detail.productName());
            Label tags = new Label(detail.tags() == null ? "" : detail.tags());
            tags.getStyleClass().add("hint-label");
            VBox nameBox = new VBox(2, name, tags);
            HBox.setHgrow(nameBox, Priority.ALWAYS);
            Label qty = new Label("x" + detail.quantity());
            Label total = new Label(FormatUtils.formatMoney(detail.lineTotal()));
            row.getChildren().addAll(nameBox, qty, total);
            orderLinesBox.getChildren().add(row);
        }
    }

    @FXML
    private void onReprintSelected() {
        OrderHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showToast("warning", "Selectionnez une commande");
            return;
        }
        PrintTicketType type = PrintTicketDialog.showDialog(currentStage());
        if (type == null) {
            return;
        }
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return type == PrintTicketType.INVOICE
                        ? printQueueService.queueInvoiceForOrder(selected.orderId())
                        : printQueueService.requeueReceiptForOrder(selected.orderId());
            }
        };
        task.setOnSucceeded(evt -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                showToast("success", "Impression reenfilee");
            } else {
                showToast("warning", "Impression introuvable");
            }
        });
        task.setOnFailed(evt -> {
            LOG.error("Erreur reimpression", task.getException());
            showToast("error", "Reimpression impossible");
        });
        Thread thread = new Thread(task, "reprint-ticket");
        thread.setDaemon(true);
        thread.start();
    }

    private Stage currentStage() {
        if (historyTable == null || historyTable.getScene() == null) {
            return null;
        }
        return (Stage) historyTable.getScene().getWindow();
    }

    @FXML
    private void onExportCsv() {
        List<OrderHistoryRow> rows = new ArrayList<>(historyFiltered);
        SalesSummary summary = loadSummarySafe();
        List<TopItem> topItems = loadTopItemsSafe(EXPORT_LIMIT);
        List<IngredientUsageRow> topIngredients = loadTopIngredientsSafe(EXPORT_LIMIT);
        List<IngredientMovementSummaryRow> ingredientMovements = loadIngredientMovementsSafe(EXPORT_LIMIT);
        List<OrderLineExportRow> orderLines = loadOrderLinesSafe();
        List<SessionRow> sessions = loadSessionsSafe();
        List<CashMovementRow> cashMovements = loadCashMovementsSafe();

        if (!hasExportData(rows, topItems, topIngredients, ingredientMovements, orderLines, sessions, cashMovements)) {
            showToast("warning", "Aucune donnee a exporter");
            return;
        }
        Path path = choosePath("Exporter CSV", "CSV", "*.csv", "csv");
        if (path == null) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeSummarySection(writer, summary);
            writeTopProductsSection(writer, topItems);
            writeTopIngredientsSection(writer, topIngredients);
            writeIngredientMovementsSection(writer, ingredientMovements);
            writeOrdersSection(writer, rows);
            writeOrderLinesSection(writer, orderLines);
            writeSessionsSection(writer, sessions);
            writeCashMovementsSection(writer, cashMovements);
            showToast("success", "Export CSV termine");
        } catch (Exception ex) {
            LOG.error("Erreur export CSV", ex);
            showToast("error", "Export CSV impossible");
        }
    }

    @FXML
    private void onExportExcel() {
        List<OrderHistoryRow> rows = new ArrayList<>(historyFiltered);
        SalesSummary summary = loadSummarySafe();
        List<TopItem> topItems = loadTopItemsSafe(EXPORT_LIMIT);
        List<IngredientUsageRow> topIngredients = loadTopIngredientsSafe(EXPORT_LIMIT);
        List<IngredientMovementSummaryRow> ingredientMovements = loadIngredientMovementsSafe(EXPORT_LIMIT);
        List<OrderLineExportRow> orderLines = loadOrderLinesSafe();
        List<SessionRow> sessions = loadSessionsSafe();
        List<CashMovementRow> cashMovements = loadCashMovementsSafe();

        if (!hasExportData(rows, topItems, topIngredients, ingredientMovements, orderLines, sessions, cashMovements)) {
            showToast("warning", "Aucune donnee a exporter");
            return;
        }
        Path path = choosePath("Exporter Excel", "Excel", "*.xls", "xls");
        if (path == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\"></head><body>");
        appendSummaryTable(sb, summary);
        appendTopProductsTable(sb, topItems);
        appendTopIngredientsTable(sb, topIngredients);
        appendIngredientMovementsTable(sb, ingredientMovements);
        appendOrdersTable(sb, rows);
        appendOrderLinesTable(sb, orderLines);
        appendSessionsTable(sb, sessions);
        appendCashMovementsTable(sb, cashMovements);
        sb.append("</body></html>");
        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            showToast("success", "Export Excel termine");
        } catch (Exception ex) {
            LOG.error("Erreur export Excel", ex);
            showToast("error", "Export Excel impossible");
        }
    }

    @FXML
    private void onExportPdf() {
        List<OrderHistoryRow> rows = new ArrayList<>(historyFiltered);
        SalesSummary summary = loadSummarySafe();
        List<TopItem> topItems = loadTopItemsSafe(TOP_LIMIT);
        List<IngredientUsageRow> topIngredients = loadTopIngredientsSafe(TOP_LIMIT);
        List<IngredientMovementSummaryRow> ingredientMovements = loadIngredientMovementsSafe(TOP_LIMIT);

        if (!hasExportData(rows, topItems, topIngredients, ingredientMovements, List.of(), List.of(), List.of())) {
            showToast("warning", "Aucune donnee a exporter");
            return;
        }
        Path path = choosePath("Exporter PDF", "PDF", "*.pdf", "pdf");
        if (path == null) {
            return;
        }

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float margin = 40;
            float y = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                y = writePdfLine(content, y, 14, "Rapport ventes " + rangeStart + " -> " + rangeEnd);
                y = writePdfLine(content, y, 11, "Total: " + FormatUtils.formatMoney(summary.total())
                        + " | Commandes: " + summary.orderCount()
                        + " | Net: " + FormatUtils.formatMoney(summary.netRevenue()));
                y -= 6;

                y = writePdfLine(content, y, 11, "Top produits vendus");
                y = writePdfLine(content, y, 10, "# | Produit | Qte | CA");
                int rank = 1;
                for (TopItem item : topItems) {
                    y = writePdfLine(content, y, 9, rank + " | " + safePdf(item.name()) + " | "
                            + item.quantity() + " | " + String.format("%.2f", item.revenue()));
                    rank++;
                    if (y < 120) {
                        break;
                    }
                }
                y -= 4;

                y = writePdfLine(content, y, 11, "Ingredients utilises (ventes)");
                y = writePdfLine(content, y, 10, "# | Ingredient | Unite | Qte | Cout");
                rank = 1;
                for (IngredientUsageRow row : topIngredients) {
                    y = writePdfLine(content, y, 9, rank + " | " + safePdf(row.name()) + " | "
                            + safePdf(row.unit()) + " | " + String.format("%.2f", row.quantity())
                            + " | " + String.format("%.2f", row.totalCost()));
                    rank++;
                    if (y < 120) {
                        break;
                    }
                }
                y -= 4;

                y = writePdfLine(content, y, 11, "Mouvements ingredients (stock)");
                y = writePdfLine(content, y, 10, "# | Ingredient | In | Out | Net");
                rank = 1;
                for (IngredientMovementSummaryRow row : ingredientMovements) {
                    y = writePdfLine(content, y, 9, rank + " | " + safePdf(row.name()) + " | "
                            + String.format("%.2f", row.inflow()) + " | "
                            + String.format("%.2f", row.outflow()) + " | "
                            + String.format("%.2f", row.net()));
                    rank++;
                    if (y < 120) {
                        break;
                    }
                }

                y -= 6;
                y = writePdfLine(content, y, 10, "# | Date | Total | Paiement | Client | Utilisateur");
                y = writePdfLine(content, y, 10, "-----------------------------------------------------------------------");

                int limit = Math.min(rows.size(), 34);
                for (int i = 0; i < limit; i++) {
                    OrderHistoryRow row = rows.get(i);
                    String line = row.orderId() + " | "
                            + safePdf(row.createdAt()) + " | "
                            + String.format("%.2f", row.total()) + " | "
                            + row.paymentType().name() + " | "
                            + safePdf(row.clientName()) + " | "
                            + safePdf(row.userName());
                    y = writePdfLine(content, y, 9, line);
                    if (y < 70) {
                        break;
                    }
                }
            }

            doc.save(path.toFile());
            showToast("success", "Export PDF termine");
        } catch (Exception ex) {
            LOG.error("Erreur export PDF", ex);
            showToast("error", "Export PDF impossible");
        }
    }

    private SalesSummary loadSummarySafe() {
        try {
            return reportService.getSummary(rangeStart, rangeEnd);
        } catch (Exception ex) {
            LOG.error("Erreur chargement summary", ex);
            return new SalesSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private List<TopItem> loadTopItemsSafe(int limit) {
        try {
            return reportService.getTopItems(rangeStart, rangeEnd, limit);
        } catch (Exception ex) {
            LOG.error("Erreur chargement top items", ex);
            return List.of();
        }
    }

    private List<IngredientUsageRow> loadTopIngredientsSafe(int limit) {
        try {
            return reportService.getTopIngredientsBySales(rangeStart, rangeEnd, limit);
        } catch (Exception ex) {
            LOG.error("Erreur chargement ingredients", ex);
            return List.of();
        }
    }

    private List<IngredientMovementSummaryRow> loadIngredientMovementsSafe(int limit) {
        try {
            return reportService.getIngredientMovementSummary(rangeStart, rangeEnd, limit);
        } catch (Exception ex) {
            LOG.error("Erreur chargement mouvements ingredients", ex);
            return List.of();
        }
    }

    private List<OrderLineExportRow> loadOrderLinesSafe() {
        try {
            return reportService.getOrderLineExports(rangeStart, rangeEnd);
        } catch (Exception ex) {
            LOG.error("Erreur chargement lignes", ex);
            return List.of();
        }
    }

    private List<SessionRow> loadSessionsSafe() {
        try {
            return reportService.getSessions(rangeStart, rangeEnd);
        } catch (Exception ex) {
            LOG.error("Erreur chargement sessions", ex);
            return List.of();
        }
    }

    private List<CashMovementRow> loadCashMovementsSafe() {
        try {
            return reportService.getCashMovements(rangeStart, rangeEnd);
        } catch (Exception ex) {
            LOG.error("Erreur chargement mouvements caisse", ex);
            return List.of();
        }
    }

    private void writeSummarySection(BufferedWriter writer, SalesSummary summary) throws Exception {
        writer.write("SECTION;Resume\n");
        writer.write("debut;fin;total;commandes;especes;prepayes;cout_ingredients;marge_brute;sorties;net\n");
        writer.write(rangeStart + ";" + rangeEnd + ";" + summary.total() + ";" + summary.orderCount() + ";"
                + summary.cashTotal() + ";" + summary.prepaidTotal() + ";" + summary.ingredientCost() + ";"
                + summary.grossProfit() + ";" + summary.cashWithdrawals() + ";" + summary.netRevenue() + "\n\n");
    }

    private void writeTopProductsSection(BufferedWriter writer, List<TopItem> items) throws Exception {
        writer.write("SECTION;Top produits\n");
        writer.write("rang;produit;quantite;chiffre_affaires\n");
        int rank = 1;
        for (TopItem item : items) {
            writer.write(rank++ + ";" + escape(item.name()) + ";" + item.quantity() + ";" + item.revenue() + "\n");
        }
        writer.write("\n");
    }

    private void writeTopIngredientsSection(BufferedWriter writer, List<IngredientUsageRow> items) throws Exception {
        writer.write("SECTION;Top ingredients (ventes)\n");
        writer.write("rang;ingredient;unite;quantite;cout_total\n");
        int rank = 1;
        for (IngredientUsageRow row : items) {
            writer.write(rank++ + ";" + escape(row.name()) + ";" + escape(row.unit()) + ";"
                    + row.quantity() + ";" + row.totalCost() + "\n");
        }
        writer.write("\n");
    }

    private void writeIngredientMovementsSection(BufferedWriter writer, List<IngredientMovementSummaryRow> rows)
            throws Exception {
        writer.write("SECTION;Mouvements ingredients\n");
        writer.write("rang;ingredient;unite;entrees;sorties;net;cout_total\n");
        int rank = 1;
        for (IngredientMovementSummaryRow row : rows) {
            writer.write(rank++ + ";" + escape(row.name()) + ";" + escape(row.unit()) + ";"
                    + row.inflow() + ";" + row.outflow() + ";" + row.net() + ";" + row.totalCost() + "\n");
        }
        writer.write("\n");
    }

    private void writeOrdersSection(BufferedWriter writer, List<OrderHistoryRow> rows) throws Exception {
        writer.write("SECTION;Commandes\n");
        writer.write("id;date;articles;total;cout_ingredients;marge_brute;paiement;client_id;client;utilisateur\n");
        for (OrderHistoryRow row : rows) {
            String clientId = row.clientId() == null ? "" : String.valueOf(row.clientId());
            writer.write(row.orderId() + ";" + escape(row.createdAt()) + ";" + row.itemCount() + ";"
                    + row.total() + ";" + row.ingredientCost() + ";" + row.grossProfit() + ";"
                    + row.paymentType().name() + ";" + clientId + ";" + escape(row.clientName()) + ";"
                    + escape(row.userName()));
            writer.write("\n");
        }
        writer.write("\n");
    }

    private void writeOrderLinesSection(BufferedWriter writer, List<OrderLineExportRow> rows) throws Exception {
        writer.write("SECTION;Lignes commande\n");
        writer.write("commande_id;date;produit;quantite;prix_unitaire;total_ligne;tags;paiement;client_id;client;utilisateur\n");
        for (OrderLineExportRow row : rows) {
            String clientId = row.clientId() == null ? "" : String.valueOf(row.clientId());
            writer.write(row.orderId() + ";" + escape(row.createdAt()) + ";" + escape(row.productName()) + ";"
                    + row.quantity() + ";" + row.unitPrice() + ";" + row.lineTotal() + ";"
                    + escape(row.tags()) + ";" + escape(row.paymentType()) + ";" + clientId + ";"
                    + escape(row.clientName()) + ";" + escape(row.userName()) + "\n");
        }
        writer.write("\n");
    }

    private void writeSessionsSection(BufferedWriter writer, List<SessionRow> rows) throws Exception {
        writer.write("SECTION;Sessions\n");
        writer.write("id;ouverture;fermeture;commandes;total;especes;prepayes;mode\n");
        for (SessionRow row : rows) {
            writer.write(row.sessionId() + ";" + escape(row.openedAt()) + ";" + escape(row.closedAt()) + ";"
                    + row.orderCount() + ";" + row.total() + ";" + row.cashTotal() + ";"
                    + row.prepaidTotal() + ";" + escape(row.closeMode()) + "\n");
        }
        writer.write("\n");
    }

    private void writeCashMovementsSection(BufferedWriter writer, List<CashMovementRow> rows) throws Exception {
        writer.write("SECTION;Mouvements caisse\n");
        writer.write("date;type;categorie;montant;utilisateur;note\n");
        for (CashMovementRow row : rows) {
            writer.write(escape(row.createdAt()) + ";" + escape(row.movementType()) + ";"
                    + escape(row.category()) + ";" + row.amount() + ";"
                    + escape(row.userName()) + ";" + escape(row.description()) + "\n");
        }
        writer.write("\n");
    }

    private void appendSummaryTable(StringBuilder sb, SalesSummary summary) {
        sb.append("<h3>Resume</h3><table border=\"1\">")
            .append("<tr><th>Debut</th><th>Fin</th><th>Total</th><th>Commandes</th><th>Especes</th><th>Prepayes</th>"
                + "<th>Cout ingredients</th><th>Marge brute</th><th>Sorties</th><th>Net</th></tr>")
                .append("<tr><td>").append(rangeStart).append("</td><td>").append(rangeEnd).append("</td><td>")
                .append(summary.total()).append("</td><td>").append(summary.orderCount()).append("</td><td>")
                .append(summary.cashTotal()).append("</td><td>").append(summary.prepaidTotal()).append("</td><td>")
                .append(summary.ingredientCost()).append("</td><td>").append(summary.grossProfit()).append("</td><td>")
                .append(summary.cashWithdrawals()).append("</td><td>").append(summary.netRevenue()).append("</td></tr>")
                .append("</table>");
    }

    private void appendTopProductsTable(StringBuilder sb, List<TopItem> items) {
        sb.append("<h3>Top produits</h3><table border=\"1\">")
            .append("<tr><th>Rang</th><th>Produit</th><th>Quantite</th><th>Chiffre d'affaires</th></tr>");
        int rank = 1;
        for (TopItem item : items) {
            sb.append("<tr><td>").append(rank++).append("</td><td>").append(escapeHtml(item.name()))
                    .append("</td><td>").append(item.quantity()).append("</td><td>")
                    .append(item.revenue()).append("</td></tr>");
        }
        sb.append("</table>");
    }

    private void appendTopIngredientsTable(StringBuilder sb, List<IngredientUsageRow> rows) {
        sb.append("<h3>Top ingredients (ventes)</h3><table border=\"1\">")
            .append("<tr><th>Rang</th><th>Ingredient</th><th>Unite</th><th>Quantite</th><th>Cout total</th></tr>");
        int rank = 1;
        for (IngredientUsageRow row : rows) {
            sb.append("<tr><td>").append(rank++).append("</td><td>").append(escapeHtml(row.name()))
                    .append("</td><td>").append(escapeHtml(row.unit())).append("</td><td>")
                    .append(row.quantity()).append("</td><td>").append(row.totalCost()).append("</td></tr>");
        }
        sb.append("</table>");
    }

    private void appendIngredientMovementsTable(StringBuilder sb, List<IngredientMovementSummaryRow> rows) {
        sb.append("<h3>Mouvements ingredients</h3><table border=\"1\">")
            .append("<tr><th>Rang</th><th>Ingredient</th><th>Unite</th><th>Entrees</th><th>Sorties</th>"
                + "<th>Net</th><th>Cout total</th></tr>");
        int rank = 1;
        for (IngredientMovementSummaryRow row : rows) {
            sb.append("<tr><td>").append(rank++).append("</td><td>").append(escapeHtml(row.name()))
                    .append("</td><td>").append(escapeHtml(row.unit())).append("</td><td>")
                    .append(row.inflow()).append("</td><td>").append(row.outflow()).append("</td><td>")
                    .append(row.net()).append("</td><td>").append(row.totalCost()).append("</td></tr>");
        }
        sb.append("</table>");
    }

    private void appendOrdersTable(StringBuilder sb, List<OrderHistoryRow> rows) {
        sb.append("<h3>Commandes</h3><table border=\"1\">")
            .append("<tr><th>ID</th><th>Date</th><th>Articles</th><th>Total</th><th>Cout ingredients</th>"
                + "<th>Marge brute</th><th>Paiement</th><th>Client ID</th><th>Client</th><th>Utilisateur</th></tr>");
        for (OrderHistoryRow row : rows) {
            sb.append("<tr>")
                    .append("<td>").append(row.orderId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.createdAt())).append("</td>")
                    .append("<td>").append(row.itemCount()).append("</td>")
                    .append("<td>").append(row.total()).append("</td>")
                    .append("<td>").append(row.ingredientCost()).append("</td>")
                    .append("<td>").append(row.grossProfit()).append("</td>")
                    .append("<td>").append(escapeHtml(row.paymentType().name())).append("</td>")
                    .append("<td>").append(row.clientId() == null ? "" : row.clientId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.clientName())).append("</td>")
                    .append("<td>").append(escapeHtml(row.userName())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendOrderLinesTable(StringBuilder sb, List<OrderLineExportRow> rows) {
        sb.append("<h3>Lignes commande</h3><table border=\"1\">")
            .append("<tr><th>Commande ID</th><th>Date</th><th>Produit</th><th>Qte</th><th>Prix unitaire</th>"
                + "<th>Total ligne</th><th>Tags</th><th>Paiement</th><th>Client ID</th><th>Client</th>"
                + "<th>Utilisateur</th></tr>");
        for (OrderLineExportRow row : rows) {
            sb.append("<tr>")
                    .append("<td>").append(row.orderId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.createdAt())).append("</td>")
                    .append("<td>").append(escapeHtml(row.productName())).append("</td>")
                    .append("<td>").append(row.quantity()).append("</td>")
                    .append("<td>").append(row.unitPrice()).append("</td>")
                    .append("<td>").append(row.lineTotal()).append("</td>")
                    .append("<td>").append(escapeHtml(row.tags())).append("</td>")
                    .append("<td>").append(escapeHtml(row.paymentType())).append("</td>")
                    .append("<td>").append(row.clientId() == null ? "" : row.clientId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.clientName())).append("</td>")
                    .append("<td>").append(escapeHtml(row.userName())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendSessionsTable(StringBuilder sb, List<SessionRow> rows) {
        sb.append("<h3>Sessions</h3><table border=\"1\">")
            .append("<tr><th>ID</th><th>Ouverture</th><th>Fermeture</th><th>Commandes</th><th>Total</th><th>Especes</th>"
                + "<th>Prepayes</th><th>Mode</th></tr>");
        for (SessionRow row : rows) {
            sb.append("<tr>")
                    .append("<td>").append(row.sessionId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.openedAt())).append("</td>")
                    .append("<td>").append(escapeHtml(row.closedAt())).append("</td>")
                    .append("<td>").append(row.orderCount()).append("</td>")
                    .append("<td>").append(row.total()).append("</td>")
                    .append("<td>").append(row.cashTotal()).append("</td>")
                    .append("<td>").append(row.prepaidTotal()).append("</td>")
                    .append("<td>").append(escapeHtml(row.closeMode())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendCashMovementsTable(StringBuilder sb, List<CashMovementRow> rows) {
        sb.append("<h3>Mouvements caisse</h3><table border=\"1\">")
            .append("<tr><th>Date</th><th>Type</th><th>Categorie</th><th>Montant</th><th>Utilisateur</th><th>Note</th></tr>");
        for (CashMovementRow row : rows) {
            sb.append("<tr>")
                    .append("<td>").append(escapeHtml(row.createdAt())).append("</td>")
                    .append("<td>").append(escapeHtml(row.movementType())).append("</td>")
                    .append("<td>").append(escapeHtml(row.category())).append("</td>")
                    .append("<td>").append(row.amount()).append("</td>")
                    .append("<td>").append(escapeHtml(row.userName())).append("</td>")
                    .append("<td>").append(escapeHtml(row.description())).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
    }

    private float writePdfLine(PDPageContentStream content, float y, int size, String text) throws Exception {
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, size);
        content.newLineAtOffset(40, y);
        content.showText(safePdf(text));
        content.endText();
        return y - (size + 4);
    }

    private String safePdf(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ");
    }

    private Path choosePath(String title, String label, String extension, String extensionName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, extension));
        Path exportDir = resolveExportDir();
        if (exportDir != null && Files.isDirectory(exportDir)) {
            chooser.setInitialDirectory(exportDir.toFile());
        }
        chooser.setInitialFileName(suggestExportFileName(extensionName, exportDir));
        if (historyTable.getScene() == null) {
            return null;
        }
        java.io.File file = chooser.showSaveDialog(historyTable.getScene().getWindow());
        if (file == null) {
            return null;
        }
        rememberExportDir(file.toPath());
        return file.toPath();
    }

    private String suggestExportFileName(String extension, Path exportDir) {
        String date = LocalDate.now().format(EXPORT_DATE);
        String ext = extension == null ? "" : extension.trim();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        int index = 1;
        while (exportDir != null) {
            String candidate = "cg_" + date + index + ext;
            if (!Files.exists(exportDir.resolve(candidate))) {
                return candidate;
            }
            index++;
        }
        return "cg_" + date + index + ext;
    }

    private boolean hasExportData(List<OrderHistoryRow> orders,
                                  List<TopItem> topItems,
                                  List<IngredientUsageRow> ingredients,
                                  List<IngredientMovementSummaryRow> movements,
                                  List<OrderLineExportRow> orderLines,
                                  List<SessionRow> sessions,
                                  List<CashMovementRow> cashMovements) {
        return (orders != null && !orders.isEmpty())
                || (topItems != null && !topItems.isEmpty())
                || (ingredients != null && !ingredients.isEmpty())
                || (movements != null && !movements.isEmpty())
                || (orderLines != null && !orderLines.isEmpty())
                || (sessions != null && !sessions.isEmpty())
                || (cashMovements != null && !cashMovements.isEmpty());
    }

    private Path resolveExportDir() {
        try {
            String value = settingsDAO.getValue(EXPORT_DIR_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }
            return Path.of(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private void rememberExportDir(Path file) {
        if (file == null) {
            return;
        }
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        try {
            settingsDAO.setValue(EXPORT_DIR_KEY, parent.toString());
        } catch (Exception ex) {
            LOG.warn("Impossible de sauvegarder le dossier export", ex);
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(";", ",");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String formatClientLabel(OrderHistoryRow row) {
        if (row == null || row.clientId() == null) {
            return "Sans client";
        }
        String name = row.clientName() == null || row.clientName().isBlank() ? "Client" : row.clientName();
        return "#" + row.clientId() + " " + name;
    }

    private void filterByClient(OrderHistoryRow row) {
        if (row == null || row.clientId() == null || historySearchField == null) {
            return;
        }
        historySearchField.setText(String.valueOf(row.clientId()));
        applyFilters();
        showToast("info", "Filtre client applique");
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
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
        FontIcon icon = UiIconHelper.statusIcon(type, 18);
        icon.setStyle("-fx-icon-color: " + toastColor(type) + ";");
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

    private String toastColor(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success" -> "-color-success-emphasis";
            case "warning" -> "-color-warning-emphasis";
            case "error", "danger" -> "-color-danger-emphasis";
            default -> "-color-accent-emphasis";
        };
    }

    private record ReportBundle(
            SalesSummary summary,
            List<TopItem> topItems,
            List<OrderHistoryRow> history,
            List<SessionRow> sessions,
            List<CashMovementRow> expenses
    ) {
    }
}
