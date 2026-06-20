package com.cafepos.ui;

import com.cafepos.model.Customer;
import com.cafepos.util.FormatUtils;
import com.cafepos.util.UiIconHelper;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Function;

/**
 * Dynamic prepaid-card payment dialog.
 *
 * Workflow:
 *   1. The cashier scans the RFID card (or types the UID).
 *   2. The dialog resolves the customer asynchronously and shows the
 *      cardholder name + current balance + how much cash is still due.
 *   3. The "Valider" button picks the right payment automatically:
 *        - balance ≥ total → PAY_PREPAID (full debit)
 *        - balance < total → MIXED_CASH (debit what's available, ask the
 *          cashier to collect the rest in cash via the regular cash dialog)
 *   4. "Recharger" is always available so the cashier can top up an empty
 *      or partial card without leaving the flow.
 *
 * Backwards-compatible {@link Decision} shape is preserved; the caller in
 * {@code PosController} already knows how to handle each action.
 */
public class PrepaidPaymentDialog extends BaseDialog {
    public enum Action {
        PAY_PREPAID,
        MIXED_CASH,
        TOPUP_CARD,
        CANCEL
    }

    public record Decision(String cardUid, Action action) {
    }

    private final double totalAmount;
    private final String suggestedCardUid;
    private final Function<String, Customer> cardResolver;

    private final TextField cardInput = new TextField();
    private final Label customerNameLabel = new Label();
    private final Label balanceLabel     = new Label();
    private final Label cashDueLabel     = new Label();
    private final Label statusLabel      = new Label();
    private final VBox  customerBlock    = new VBox(4);

    private Button validateButton;
    private Customer resolvedCustomer;
    private String lastResolvedUid = "";
    // Debounce so we resolve once typing/scanning pauses — required because
    // the POS is touch-only (no physical Enter key for the cashier).
    private final PauseTransition resolveDebounce = new PauseTransition(Duration.millis(350));

    private Decision result;

    private PrepaidPaymentDialog(Stage owner,
                                 double totalAmount,
                                 String suggestedCardUid,
                                 Function<String, Customer> cardResolver) {
        super(owner, 520, 460);
        this.totalAmount = Math.max(0, totalAmount);
        this.suggestedCardUid = suggestedCardUid == null ? "" : suggestedCardUid.trim();
        this.cardResolver = cardResolver;
        initializeDialog();
    }

    /**
     * @param cardResolver function that maps a card UID to a {@link Customer}
     *                     (return {@code null} when the card is not registered).
     *                     May be invoked off the JavaFX thread.
     */
    public static Decision showDialog(Stage owner,
                                      double totalAmount,
                                      String suggestedCardUid,
                                      Function<String, Customer> cardResolver) {
        PrepaidPaymentDialog dialog = new PrepaidPaymentDialog(owner, totalAmount, suggestedCardUid, cardResolver);
        dialog.showAndWait();
        return dialog.result;
    }

    /** Legacy overload kept for callers that don't have a resolver yet. */
    public static Decision showDialog(Stage owner, double totalAmount, String suggestedCardUid) {
        return showDialog(owner, totalAmount, suggestedCardUid, null);
    }

    @Override
    protected VBox buildContent() {
        VBox root = new VBox(10);

        FontIcon icon = UiIconHelper.makeIcon("mdi2c-credit-card", 18, "#6B2D1A");
        Label title = new Label("Paiement par carte prépayée");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        HBox titleRow = new HBox(8, icon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label totalLabel = new Label("Total commande : " + FormatUtils.formatMoney(totalAmount));
        totalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label cardLabel = new Label("Carte RFID");
        cardLabel.getStyleClass().add("text-muted");

        cardInput.setPromptText("Scannez ou saisissez le code carte");
        cardInput.setStyle("-fx-font-size: 14px;");
        if (!suggestedCardUid.isBlank()) {
            cardInput.setText(suggestedCardUid);
        }

        statusLabel.setText("Approchez la carte du lecteur ou saisissez le code.");
        statusLabel.getStyleClass().add("text-muted");

        // ── customer block (filled in once the card is resolved) ────────
        customerNameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        balanceLabel.setStyle("-fx-font-size: 13px;");
        cashDueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        customerBlock.getChildren().addAll(customerNameLabel, balanceLabel, cashDueLabel);
        customerBlock.setStyle(
                "-fx-background-color: #F5EFE6;" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 10;");
        customerBlock.setVisible(false);
        customerBlock.setManaged(false);

        // ── buttons ──────────────────────────────────────────────────────
        Button cancel = button("Annuler", "elevated", evt -> {
            result = new Decision(safeCardUid(), Action.CANCEL);
            close();
        });

        Button topup = button("Recharger", "warning", evt -> {
            result = new Decision(safeCardUid(), Action.TOPUP_CARD);
            close();
        });

        validateButton = button("Valider", "success", evt -> validate());
        validateButton.setDisable(true);

        HBox actions = new HBox(8, cancel, topup, validateButton);
        actions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(
                titleRow,
                totalLabel,
                new Separator(),
                cardLabel,
                cardInput,
                customerBlock,
                statusLabel,
                new Separator(),
                actions
        );

        wireCardListeners();
        return root;
    }

    private Button button(String text, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button", styleClass);
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setOnAction(handler);
        return b;
    }

    private void wireCardListeners() {
        // RFID readers emulate a keyboard and usually append Enter — handle
        // both the Enter case AND a debounced auto-resolve so a touch-only
        // cashier (no keyboard available) gets the same instant feedback as
        // soon as scanning/typing stops.
        cardInput.setOnAction(evt -> {
            resolveDebounce.stop();
            resolveCard(safeCardUid());
        });
        cardInput.textProperty().addListener((obs, oldText, newText) -> {
            String normalized = newText == null ? "" : newText.trim().toUpperCase();
            if (normalized.isBlank()) {
                resolveDebounce.stop();
                clearResolvedCustomer("Approchez la carte du lecteur ou saisissez le code.");
                return;
            }
            if (normalized.equals(lastResolvedUid)) {
                // Already resolved — nothing to do.
                return;
            }
            // Reset state and schedule a debounced resolve.
            validateButton.setDisable(true);
            statusLabel.setText("Lecture en cours…");
            resolveDebounce.stop();
            resolveDebounce.setOnFinished(e -> resolveCard(safeCardUid()));
            resolveDebounce.playFromStart();
        });

        cardInput.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    result = new Decision(safeCardUid(), Action.CANCEL);
                    close();
                    event.consume();
                } else if (event.getCode() == KeyCode.ENTER && resolvedCustomer != null) {
                    validate();
                    event.consume();
                }
            });
            Platform.runLater(() -> {
                cardInput.requestFocus();
                if (!cardInput.getText().isBlank()) {
                    resolveCard(safeCardUid());
                }
            });
        });
    }

    private void resolveCard(String uid) {
        if (cardResolver == null) {
            // No resolver supplied → keep legacy behaviour (Valider always enabled
            // so the caller can do the lookup itself).
            lastResolvedUid = uid;
            validateButton.setDisable(uid.isBlank());
            statusLabel.setText(uid.isBlank() ? "Saisir / scanner le code carte." : "Carte saisie : " + uid);
            return;
        }
        if (uid.isBlank()) {
            clearResolvedCustomer("Approchez la carte du lecteur ou saisissez le code.");
            return;
        }
        statusLabel.setText("Lecture en cours…");
        Task<Customer> task = new Task<>() {
            @Override
            protected Customer call() {
                return cardResolver.apply(uid);
            }
        };
        task.setOnSucceeded(evt -> applyResolvedCustomer(uid, task.getValue()));
        task.setOnFailed(evt -> {
            clearResolvedCustomer("Erreur de lecture, réessayez.");
        });
        Thread thread = new Thread(task, "prepaid-dialog-card-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyResolvedCustomer(String uid, Customer customer) {
        lastResolvedUid = uid;
        resolvedCustomer = customer;
        if (customer == null) {
            customerBlock.setVisible(false);
            customerBlock.setManaged(false);
            validateButton.setDisable(true);
            statusLabel.setText("Carte non reconnue. Utilisez \"Recharger\" pour créer le client.");
            return;
        }
        double balance = customer.getBalance();
        double cashDue = Math.max(0, totalAmount - balance);

        customerNameLabel.setText(customer.getName() == null ? "Client" : customer.getName());
        balanceLabel.setText("Solde carte : " + FormatUtils.formatMoney(balance));

        if (cashDue <= 0.0001) {
            cashDueLabel.setText("✓ Solde suffisant — paiement intégral par carte.");
            cashDueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2E7D32;");
            statusLabel.setText("Cliquez Valider pour débiter " + FormatUtils.formatMoney(totalAmount) + ".");
        } else {
            cashDueLabel.setText("⚠ Complément espèces requis : " + FormatUtils.formatMoney(cashDue));
            cashDueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #C5630E;");
            statusLabel.setText("Cliquez Valider : la carte sera débitée de "
                    + FormatUtils.formatMoney(balance)
                    + " et le reste collecté en espèces.");
        }

        customerBlock.setVisible(true);
        customerBlock.setManaged(true);
        validateButton.setDisable(false);
    }

    private void clearResolvedCustomer(String status) {
        resolvedCustomer = null;
        lastResolvedUid = "";
        customerBlock.setVisible(false);
        customerBlock.setManaged(false);
        validateButton.setDisable(true);
        statusLabel.setText(status);
    }

    private void validate() {
        Action action;
        if (resolvedCustomer == null) {
            // No resolver in use → caller will do the lookup; default to prepaid attempt.
            action = Action.PAY_PREPAID;
        } else if (resolvedCustomer.getBalance() + 0.0001 >= totalAmount) {
            action = Action.PAY_PREPAID;
        } else {
            action = Action.MIXED_CASH;
        }
        result = new Decision(safeCardUid(), action);
        close();
    }

    private String safeCardUid() {
        String value = cardInput.getText();
        return value == null ? "" : value.trim().toUpperCase();
    }
}
