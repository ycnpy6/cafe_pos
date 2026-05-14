package com.cafepos.hardware;

import javafx.scene.control.TextField;

import java.util.function.Consumer;

public class RFIDHandler {
    private final TextField input;
    private Consumer<String> onCard;

    public RFIDHandler(TextField input) {
        this.input = input;
        wire();
    }

    public void setOnCard(Consumer<String> onCard) {
        this.onCard = onCard;
    }

    private void wire() {
        input.setOnAction(event -> {
            String raw = input.getText();
            input.clear();
            if (raw == null) {
                return;
            }
            String uid = normalize(raw);
            if (uid.length() < 6 || uid.length() > 20) {
                return;
            }
            if (onCard != null) {
                onCard.accept(uid);
            }
        });
    }

    private String normalize(String raw) {
        return raw.trim().toUpperCase();
    }
}
