package com.cafepos.hardware;

import java.util.function.Consumer;

import javafx.scene.control.TextField;

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
        return RFIDDecoder.normalize(raw);
    }
}
