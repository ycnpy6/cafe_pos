package com.cafepos.ui;

import com.cafepos.hardware.PrinterService;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.UiIconHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class InvoiceDialog extends BaseDialog {
    private final Order order;
    private final PrinterService printerService = new PrinterService();
    private final TextField recipientName = new TextField();
    private final TextField recipientAddress = new TextField();
    private final Label errorLabel = new Label();

    private final String invoiceNumber;
    private final String orderTitle;

    private InvoiceDialog(Stage owner, Order order) {
        super(owner, 420, 480);
        this.order = order;
        this.invoiceNumber = generateInvoiceNumber();
        this.orderTitle = "Facture - Commande #" + invoiceNumber.substring(invoiceNumber.length() - 4);
        initializeDialog();
    }

    public static void showDialog(Stage owner, Order order) {
        InvoiceDialog dialog = new InvoiceDialog(owner, order);
        dialog.showAndWait();
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(10);

        FontIcon icon = UiIconHelper.makeIcon("mdi2r-receipt", 18, "#6B2D1A");
        Label title = new Label(orderTitle);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox titleRow = new HBox(8, icon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label recipientTitle = new Label("Destinataire (optionnel)");
        recipientTitle.getStyleClass().add("text-muted");

        recipientName.setPromptText("Nom du client ou entreprise");
        recipientAddress.setPromptText("Adresse (optionnel)");

        TableView<LineRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(28);
        table.setPrefHeight(200);
        table.setEditable(false);

        TableColumn<LineRow, String> productColumn = new TableColumn<>("Article");
        productColumn.setCellValueFactory(cell -> cell.getValue().product());

        TableColumn<LineRow, String> qtyColumn = new TableColumn<>("Qte");
        qtyColumn.setCellValueFactory(cell -> cell.getValue().quantity());
        qtyColumn.setMaxWidth(80);

        TableColumn<LineRow, String> unitColumn = new TableColumn<>("P.U.");
        unitColumn.setCellValueFactory(cell -> cell.getValue().unitPrice());
        unitColumn.setMaxWidth(90);

        TableColumn<LineRow, String> totalColumn = new TableColumn<>("Total");
        totalColumn.setCellValueFactory(cell -> cell.getValue().lineTotal());
        totalColumn.setMaxWidth(100);

        table.getColumns().addAll(productColumn, qtyColumn, unitColumn, totalColumn);
        table.setItems(FXCollections.observableArrayList(linesFromOrder(order.getLines())));

        VBox totalsBox = new VBox(6);
        HBox tvaRow = buildTvaRow();
        HBox discountRow = buildDiscountRow();
        HBox totalRow = buildTotalRow();

        if (tvaRow != null) {
            totalsBox.getChildren().add(tvaRow);
        }
        if (discountRow != null) {
            totalsBox.getChildren().add(discountRow);
        }
        totalsBox.getChildren().add(totalRow);

        errorLabel.getStyleClass().add("danger");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button cancel = new Button("Annuler");
        cancel.getStyleClass().addAll("button", "elevated");
        cancel.setPrefHeight(52);
        cancel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        cancel.setOnAction(evt -> close());

        Button preview = new Button("Apercu");
        preview.getStyleClass().addAll("button", "elevated");
        preview.setPrefHeight(52);
        preview.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(preview, Priority.ALWAYS);
        preview.setGraphic(UiIconHelper.makeIcon("mdi2e-eye", 16, "#6B2D1A"));
        preview.setOnAction(evt -> showPreview());

        Button print = new Button("Imprimer (thermique)");
        print.getStyleClass().addAll("button", "success");
        print.setPrefHeight(52);
        print.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(print, Priority.ALWAYS);
        print.setGraphic(UiIconHelper.makeIcon("mdi2p-printer", 16, "#FFFFFF"));
        print.setOnAction(evt -> printInvoice());

        HBox actions = new HBox(8, cancel, preview, print);

        root.getChildren().addAll(
                titleRow,
                recipientTitle,
                recipientName,
                recipientAddress,
                new Separator(),
                table,
                totalsBox,
                new Separator(),
                errorLabel,
                actions
        );

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        close();
                        event.consume();
                    } else if (event.getCode() == KeyCode.ENTER) {
                        printInvoice();
                        event.consume();
                    }
                });
            }
        });

        return root;
    }

    private List<LineRow> linesFromOrder(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> new LineRow(
                        line.getProduct().getName(),
                        String.valueOf(line.getQuantity()),
                        FormatUtils.formatMoney(line.getUnitTotal()),
                        FormatUtils.formatMoney(line.getLineTotal())
                ))
                .toList();
    }

    private HBox buildTvaRow() {
        if (order.getTvaPercent() <= 0) {
            return null;
        }
        HBox row = new HBox(8);
        Label label = new Label("TVA (" + formatPercent(order.getTvaPercent()) + "%)");
        Label value = new Label(FormatUtils.formatMoney(order.getTvaAmount()));
        value.getStyleClass().add("text-bold");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(label, spacer, value);
        return row;
    }

    private HBox buildDiscountRow() {
        if (!order.hasDiscount()) {
            return null;
        }
        HBox row = new HBox(8);
        Label label = new Label("Remise");
        Label value = new Label("-" + FormatUtils.formatMoney(order.getAppliedDiscountAmount()));
        value.getStyleClass().add("warning");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(label, spacer, value);
        return row;
    }

    private HBox buildTotalRow() {
        HBox row = new HBox(8);
        Label label = new Label("TOTAL");
        label.getStyleClass().add("text-bold");
        Label value = new Label(FormatUtils.formatMoney(order.getTotal()));
        value.getStyleClass().add("text-bold");
        value.setStyle("-fx-font-size: 20px; -fx-text-fill: -color-accent-emphasis;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(label, spacer, value);
        return row;
    }

    private void showPreview() {
        List<String> lines = printerService.buildInvoiceTextLines(
                order, -1, invoiceNumber, recipientName.getText(), recipientAddress.getText());
        TicketPreviewDialog.show(this, orderTitle, lines, this::printInvoiceSilently);
    }

    private void printInvoiceSilently() {
        try {
            printerService.printInvoice(
                    order, -1, -1, invoiceNumber, recipientName.getText(), recipientAddress.getText());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void printInvoice() {
        try {
            printerService.printInvoice(
                    order,
                    -1,
                    -1,
                    invoiceNumber,
                    recipientName.getText(),
                    recipientAddress.getText()
            );
            close();
        } catch (Exception ex) {
            errorLabel.setText("Impression impossible: " + ex.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private String formatPercent(double value) {
        return String.format(java.util.Locale.FRANCE, "%.0f", value);
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int suffix = (int) (System.currentTimeMillis() % 10_000);
        return datePart + "-" + String.format("%04d", suffix);
    }

    private record LineRow(SimpleStringProperty product,
                           SimpleStringProperty quantity,
                           SimpleStringProperty unitPrice,
                           SimpleStringProperty lineTotal) {
        private LineRow(String product, String quantity, String unitPrice, String lineTotal) {
            this(new SimpleStringProperty(product),
                    new SimpleStringProperty(quantity),
                    new SimpleStringProperty(unitPrice),
                    new SimpleStringProperty(lineTotal));
        }
    }
}
