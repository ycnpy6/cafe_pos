package com.cafepos.controllers;

import com.cafepos.model.CashMovementRow;
import com.cafepos.model.OrderHistoryRow;
import com.cafepos.model.OrderLineDetail;
import com.cafepos.model.PaymentType;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.SessionRow;
import com.cafepos.model.TopItem;
import com.cafepos.service.PrintQueueService;
import com.cafepos.service.ReportService;
import com.cafepos.util.FormatUtils;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReportsController {
    private static final Logger LOG = LoggerFactory.getLogger(ReportsController.class);
    private static final int MAX_TOASTS = 3;
    private static final int TOP_LIMIT = 10;

    private final ReportService reportService = new ReportService();
    private final PrintQueueService printQueueService = PrintQueueService.getInstance();

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
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return printQueueService.requeueReceiptForOrder(selected.orderId());
            }
        };
        task.setOnSucceeded(evt -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                showToast("success", "Ticket reenfile");
            } else {
                showToast("warning", "Ticket introuvable");
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

    @FXML
    private void onExportCsv() {
        List<OrderHistoryRow> rows = new ArrayList<>(historyFiltered);
        if (rows.isEmpty()) {
            showToast("warning", "Aucune donnee a exporter");
            return;
        }
        Path path = choosePath("Exporter CSV", "CSV", "*.csv");
        if (path == null) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("id;date;items;total;cout_ingredients;marge_brute;paiement;client_id;client_nom;utilisateur\n");
            for (OrderHistoryRow row : rows) {
                String clientId = row.clientId() == null ? "" : String.valueOf(row.clientId());
                writer.write(row.orderId() + ";" + escape(row.createdAt()) + ";" + row.itemCount() + ";"
                        + row.total() + ";" + row.ingredientCost() + ";" + row.grossProfit() + ";"
                        + row.paymentType().name() + ";" + clientId + ";" + escape(row.clientName()) + ";"
                        + escape(row.userName()));
                writer.write("\n");
            }
            showToast("success", "Export CSV termine");
        } catch (Exception ex) {
            LOG.error("Erreur export CSV", ex);
            showToast("error", "Export CSV impossible");
        }
    }

    @FXML
    private void onExportExcel() {
        List<OrderHistoryRow> rows = new ArrayList<>(historyFiltered);
        if (rows.isEmpty()) {
            showToast("warning", "Aucune donnee a exporter");
            return;
        }
        Path path = choosePath("Exporter Excel", "Excel", "*.xls");
        if (path == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\"></head><body><table border=\"1\">");
        sb.append("<tr><th>ID</th><th>Date</th><th>Items</th><th>Total</th><th>Cout ingredients</th>"
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
        sb.append("</table></body></html>");
        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            showToast("success", "Export Excel termine");
        } catch (Exception ex) {
            LOG.error("Erreur export Excel", ex);
            showToast("error", "Export Excel impossible");
        }
    }

    private Path choosePath(String title, String label, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, extension));
        if (historyTable.getScene() == null) {
            return null;
        }
        java.io.File file = chooser.showSaveDialog(historyTable.getScene().getWindow());
        if (file == null) {
            return null;
        }
        return file.toPath();
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
            case "error" -> "X";
            case "warning" -> "!";
            default -> "i";
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
