package com.cafepos.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtils {
    private static final DecimalFormat MONEY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        MONEY_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    private FormatUtils() {
    }

    public static String formatMoney(double amount) {
        return MONEY_FORMAT.format(amount) + " DZD";
    }
}
