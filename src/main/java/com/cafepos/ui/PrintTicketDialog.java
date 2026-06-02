package com.cafepos.ui;

import com.cafepos.model.PrintTicketType;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public final class PrintTicketDialog {
    private PrintTicketDialog() {
    }

    public static PrintTicketType showDialog(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Impression");
        alert.setHeaderText("Choisir le format d'impression");
        alert.setContentText("Ticket ou facture ?");
        if (owner != null) {
            alert.initOwner(owner);
        }

        ButtonType ticket = new ButtonType("Ticket");
        ButtonType invoice = new ButtonType("Facture");
        alert.getButtonTypes().setAll(ticket, invoice, ButtonType.CANCEL);

        return alert.showAndWait()
                .map(button -> {
                    if (button == ticket) {
                        return PrintTicketType.RECEIPT;
                    }
                    if (button == invoice) {
                        return PrintTicketType.INVOICE;
                    }
                    return null;
                })
                .orElse(null);
    }
}
