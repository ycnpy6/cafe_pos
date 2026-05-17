package com.cafepos.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtils {
    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat MONEY_NO_DECIMALS;
    private static final DateTimeFormatter INPUT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        MONEY_FORMAT = new DecimalFormat("#,##0.00", symbols);
        MONEY_NO_DECIMALS = new DecimalFormat("#,##0", symbols);
    }

    private FormatUtils() {
    }

    public static String formatMoney(double amount) {
        boolean whole = Math.abs(amount - Math.rint(amount)) < 0.0001;
        String formatted = whole ? MONEY_NO_DECIMALS.format(amount) : MONEY_FORMAT.format(amount);
        return formatted + " DZD";
    }

    public static String formatDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(value, INPUT_DATETIME);
            return parsed.format(OUTPUT_DATETIME);
        } catch (Exception ex) {
            return value;
        }
    }
}
