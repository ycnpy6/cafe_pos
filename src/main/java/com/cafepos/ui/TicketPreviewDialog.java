package com.cafepos.ui;

import com.cafepos.hardware.PrinterService;
import com.cafepos.util.UiIconHelper;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Apercu texte d'un ticket/facture avant impression, avec deux voies
 * d'impression distinctes : une imprimante ticket thermique (ESC/POS, via
 * l'action fournie par l'appelant) ou une imprimante Windows standard
 * (jet d'encre/laser — ex: Epson) via PrinterService.printPlainText, qui ne
 * comprend pas les commandes ESC/POS et a donc besoin d'un rendu texte
 * classique.
 */
public final class TicketPreviewDialog extends BaseDialog {
    private final String title;
    private final List<String> lines;
    private final Runnable thermalPrintAction;
    private final PrinterService printerService = new PrinterService();

    private Label statusLabel;
    private ComboBox<String> printerCombo;

    private TicketPreviewDialog(Stage owner, String title, List<String> lines, Runnable thermalPrintAction) {
        super(owner, 460, 620);
        this.title = title;
        this.lines = lines;
        this.thermalPrintAction = thermalPrintAction;
        initializeDialog();
    }

    /**
     * @param thermalPrintAction action a executer pour une impression ESC/POS
     *                           classique (ticket thermique) ; peut etre null
     *                           si aucune imprimante thermique n'est utilisee.
     */
    public static void show(Stage owner, String title, List<String> lines, Runnable thermalPrintAction) {
        new TicketPreviewDialog(owner, title, lines, thermalPrintAction).showAndWait();
    }

    @Override
    protected VBox buildContent() {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-3");

        TextArea preview = new TextArea(String.join("\n", lines));
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefRowCount(20);
        preview.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(preview, Priority.ALWAYS);

        Label printerLabel = new Label("Imprimante standard (PDF/texte, ex: Epson)");
        printerLabel.getStyleClass().add("text-muted");
        printerCombo = new ComboBox<>();
        printerCombo.setMaxWidth(Double.MAX_VALUE);
        loadPrinterNames();

        statusLabel = new Label("");
        statusLabel.getStyleClass().add("text-muted");

        Button printStandard = new Button("Imprimer sur cette imprimante");
        printStandard.getStyleClass().addAll("button", "success");
        printStandard.setMaxWidth(Double.MAX_VALUE);
        printStandard.setGraphic(UiIconHelper.makeIcon("mdi2p-printer", 16, "#FFFFFF"));
        printStandard.setOnAction(evt -> printOnStandardPrinter());

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        Button close = new Button("Fermer");
        close.getStyleClass().addAll("button", "flat");
        close.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(close, Priority.ALWAYS);
        close.setOnAction(evt -> close());

        if (thermalPrintAction != null) {
            Button printThermal = new Button("Imprimer (ticket thermique)");
            printThermal.getStyleClass().addAll("button", "elevated");
            printThermal.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(printThermal, Priority.ALWAYS);
            printThermal.setOnAction(evt -> {
                try {
                    thermalPrintAction.run();
                    statusLabel.setText("Envoye a l'imprimante thermique");
                } catch (Exception ex) {
                    statusLabel.setText("Erreur: " + ex.getMessage());
                }
            });
            actions.getChildren().addAll(close, printThermal);
        } else {
            actions.getChildren().add(close);
        }

        VBox root = new VBox(10, titleLabel, preview, printerLabel, printerCombo, printStandard, statusLabel, actions);
        root.setPrefWidth(440);
        return root;
    }

    private void loadPrinterNames() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return printerService.getPrinterNames();
            }
        };
        task.setOnSucceeded(evt -> {
            printerCombo.getItems().setAll(task.getValue());
            if (!printerCombo.getItems().isEmpty()) {
                task.getValue().stream()
                        .filter(name -> name.toLowerCase().contains("epson"))
                        .findFirst()
                        .ifPresentOrElse(
                                printerCombo.getSelectionModel()::select,
                                printerCombo.getSelectionModel()::selectFirst
                        );
            }
        });
        Thread thread = new Thread(task, "preview-printer-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void printOnStandardPrinter() {
        String selected = printerCombo.getSelectionModel().getSelectedItem();
        statusLabel.setText("Impression en cours...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                printerService.printPlainText(lines, selected);
                return null;
            }
        };
        task.setOnSucceeded(evt -> statusLabel.setText("Ticket envoye a " + (selected == null ? "l'imprimante par defaut" : selected)));
        task.setOnFailed(evt -> {
            Throwable ex = task.getException();
            statusLabel.setText("Erreur: " + (ex == null || ex.getMessage() == null ? "impression impossible" : ex.getMessage()));
        });
        Thread thread = new Thread(task, "preview-print-standard");
        thread.setDaemon(true);
        thread.start();
    }
}
