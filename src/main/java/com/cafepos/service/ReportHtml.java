package com.cafepos.service;

import java.util.Locale;

/**
 * Aide de mise en forme partagee par les exports quotidiens et hebdomadaires :
 * meme charte (couleurs de la marque, zebrures, totaux) pour tous les
 * classeurs .xls (HTML ouvert nativement par Excel).
 */
final class ReportHtml {
    static final String BRAND_PRIMARY = "#6B2D1A";
    static final String BRAND_BG = "#F5ECD7";
    static final String ZEBRA_BG = "#FAF4E6";

    private ReportHtml() {
    }

    static void documentStart(StringBuilder sb, String subtitle) {
        sb.append("<html><head><meta charset=\"UTF-8\"><style>")
          .append("body{font-family:Calibri,Arial,sans-serif;background:").append(BRAND_BG).append(";}")
          .append("h1{color:").append(BRAND_PRIMARY).append(";font-size:22pt;margin:4px 0 0 0;}")
          .append("h2{color:").append(BRAND_PRIMARY).append(";font-size:13pt;margin:18px 0 4px 0;}")
          .append("h3{color:").append(BRAND_PRIMARY).append(";font-size:11pt;margin:12px 0 2px 0;}")
          .append(".sub{color:#7A5C4A;font-size:10pt;margin:0 0 12px 0;}")
          .append("table{border-collapse:collapse;margin:4px 0 14px 0;}")
          .append("th{background:").append(BRAND_PRIMARY).append(";color:#FFFFFF;font-weight:bold;")
          .append("padding:6px 10px;border:1px solid ").append(BRAND_PRIMARY).append(";text-align:left;}")
          .append("td{padding:5px 10px;border:1px solid #C9B393;background:#FFFFFF;}")
          .append("tr.alt td{background:").append(ZEBRA_BG).append(";}")
          .append("td.num,th.num{text-align:right;}")
          .append("tr.total td{background:").append(BRAND_PRIMARY).append(";color:#FFFFFF;font-weight:bold;}")
          .append("td.kpi{font-weight:bold;color:").append(BRAND_PRIMARY).append(";}")
          .append("</style></head><body>")
          .append("<h1>COMMON GROUNDS</h1>")
          .append("<p class=\"sub\">").append(escape(subtitle)).append("</p>");
    }

    static void documentEnd(StringBuilder sb) {
        sb.append("</body></html>");
    }

    static String rowStart(int index) {
        return index % 2 == 1 ? "<tr class=\"alt\">" : "<tr>";
    }

    static String td(String value) {
        return "<td>" + escape(value) + "</td>";
    }

    static String tdNum(String value) {
        return "<td class=\"num\">" + escape(value) + "</td>";
    }

    /** Ne garde que l'heure (HH:mm) d'un horodatage "yyyy-MM-dd HH:mm:ss". */
    static String timeOf(String createdAt) {
        if (createdAt == null) {
            return "";
        }
        int space = createdAt.indexOf(' ');
        if (space < 0 || createdAt.length() < space + 6) {
            return createdAt;
        }
        return createdAt.substring(space + 1, space + 6);
    }

    static String money(double value) {
        return String.format(Locale.FRENCH, "%.2f", value);
    }

    static String quantity(double value) {
        return String.format(Locale.FRENCH, "%.3f", value);
    }

    /** Pourcentage de marge; vide quand la base est nulle (division par zero). */
    static String percent(double part, double base) {
        if (base == 0) {
            return "";
        }
        return String.format(Locale.FRENCH, "%.1f %%", part / base * 100.0);
    }

    static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
