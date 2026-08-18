package com.cafepos.hardware;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

import com.cafepos.dao.SettingsDAO;
import com.cafepos.model.Customer;
import com.cafepos.model.Order;
import com.cafepos.model.OrderLine;
import com.cafepos.model.SalesSummary;
import com.cafepos.model.PaymentType;
import com.cafepos.model.PrintTicketType;

public class PrinterService {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE dd MMM yyyy HH:mm", Locale.FRENCH);

    private static final String PRINTER_KEY = "printer.name";

    private static final String RECEIPT_STORE_NAME_KEY = "receipt.store.name";
    private static final String RECEIPT_PHONE_KEY = "receipt.store.phone";
    private static final String RECEIPT_TICKET_PREFIX_KEY = "receipt.ticket.prefix";
    private static final String RECEIPT_FOOTER_KEY = "receipt.footer";
    private static final String RECEIPT_CURRENCY_KEY = "receipt.currency.label";
    private static final String RECEIPT_SEPARATOR_KEY = "receipt.separator.char";
    private static final String RECEIPT_SHOW_CUSTOMER_KEY = "receipt.show.customer.block";

    private static final String DEFAULT_STORE_NAME = "COMMON GROUNDS";
    private static final String DEFAULT_PHONE = "Tel: 023 484 524";
    private static final String DEFAULT_TICKET_PREFIX = "TICKET Num";
    private static final String DEFAULT_FOOTER = "Common Grounds, Uncommon Flavors";
    private static final String DEFAULT_CURRENCY = "DA";
    private static final String DEFAULT_SEPARATOR = "*";

    private static final int LINE_WIDTH = 42;

    private final SettingsDAO settingsDAO = new SettingsDAO();

    public void printReceipt(Order order, double remainingBalance) throws Exception {
        printReceipt(order, -1, remainingBalance);
    }

    public void printReceipt(Order order, int orderId, double remainingBalance) throws Exception {
        printTicket(order, orderId, remainingBalance, PrintTicketType.RECEIPT);
    }

    public void printInvoice(Order order, int orderId, double remainingBalance) throws Exception {
        printInvoice(order, orderId, remainingBalance, null, null, null);
    }

    public void printInvoice(Order order,
                             int orderId,
                             double remainingBalance,
                             String invoiceNumber,
                             String recipientName,
                             String recipientAddress) throws Exception {
        String payload = buildInvoicePayload(order, orderId, remainingBalance, invoiceNumber, recipientName, recipientAddress);
        printPayload(payload);
    }

    private void printTicket(Order order, int orderId, double remainingBalance, PrintTicketType ticketType)
            throws Exception {
        String payload = buildTicketPayload(order, orderId, remainingBalance, ticketType);
        printPayload(payload);
    }

    public void printTestTicket() throws Exception {
        byte[] data = buildTestReceipt();
        String payload = Base64.getEncoder().encodeToString(data);
        printPayload(payload);
    }

    public String buildReceiptPayload(Order order, double remainingBalance) {
        return buildReceiptPayload(order, -1, remainingBalance);
    }

    public String buildReceiptPayload(Order order, int orderId, double remainingBalance) {
        return buildTicketPayload(order, orderId, remainingBalance, PrintTicketType.RECEIPT);
    }

    public String buildInvoicePayload(Order order, int orderId, double remainingBalance) {
        return buildInvoicePayload(order, orderId, remainingBalance, null, null, null);
    }

    public String buildInvoicePayload(Order order,
                                      int orderId,
                                      double remainingBalance,
                                      String invoiceNumber,
                                      String recipientName,
                                      String recipientAddress) {
        return buildTicketPayload(order, orderId, remainingBalance, PrintTicketType.INVOICE,
                invoiceNumber, recipientName, recipientAddress);
    }

    public String buildTicketPayload(Order order, int orderId, double remainingBalance, PrintTicketType ticketType) {
        byte[] data = buildTicket(order, orderId, remainingBalance, ticketType, null, null, null);
        return Base64.getEncoder().encodeToString(data);
    }

    public String buildTicketPayload(Order order,
                                     int orderId,
                                     double remainingBalance,
                                     PrintTicketType ticketType,
                                     String invoiceNumber,
                                     String recipientName,
                                     String recipientAddress) {
        byte[] data = buildTicket(order, orderId, remainingBalance, ticketType,
                invoiceNumber, recipientName, recipientAddress);
        return Base64.getEncoder().encodeToString(data);
    }

    public void printPayload(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload vide");
        }
        try {
            PrintService service = findPrinter();
            if (service == null) {
                throw new IllegalStateException("Imprimante non disponible");
            }
            byte[] data = Base64.getDecoder().decode(payload);
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(data, flavor, null);
            DocPrintJob job = service.createPrintJob();
            job.print(doc, null);
        } catch (Exception ex) {
            logError(ex);
            throw ex;
        }
    }

    private PrintService findPrinter() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        String saved = null;
        try {
            saved = settingsDAO.getValue(PRINTER_KEY);
        } catch (Exception ignored) {
            // Pas bloquant.
        }
        if (saved != null && !saved.isBlank()) {
            for (PrintService service : services) {
                if (service.getName().equalsIgnoreCase(saved)) {
                    return service;
                }
            }
        }
        for (PrintService service : services) {
            String name = service.getName().toLowerCase();
            if (name.contains("rongta") || name.contains("generic")) {
                return service;
            }
        }
        // Aucune configuration explicite et aucun nom reconnu : la plupart des
        // caisses n'ont qu'une imprimante (le ticket), deja definie par
        // defaut dans Windows. Sans ce repli, toute impression echouait en
        // silence (exception avalee par le dispatch de la file) des qu'aucun
        // "PRINTER_KEY" n'etait sauvegarde et que le nom du pilote ne
        // contenait ni "rongta" ni "generic".
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultService != null) {
            return defaultService;
        }
        return services.length > 0 ? services[0] : null;
    }

    public java.util.List<String> getPrinterNames() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (PrintService service : services) {
            names.add(service.getName());
        }
        return names;
    }

    private byte[] buildTicket(Order order,
                               int orderId,
                               double remainingBalance,
                               PrintTicketType ticketType,
                               String invoiceNumber,
                               String recipientName,
                               String recipientAddress) {
        if (ticketType == PrintTicketType.INVOICE) {
            return buildInvoiceTicket(order, orderId, invoiceNumber, recipientName, recipientAddress);
        }
        return buildReceiptTicket(order, orderId, remainingBalance);
    }

    private byte[] buildReceiptTicket(Order order, int orderId, double remainingBalance) {
        ReceiptTemplate template = loadTemplate();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendCommand(out, 0x1B, 0x40); // ESC @
        appendCommand(out, 0x1B, 0x74, 0x02); // ESC t 2 = codepage CP850

        setAlign(out, 1);
        setBold(out, true);
        appendLine(out, "COMMON GROUNDS");
        setBold(out, false);
        appendLine(out, "American Institute, Alger");
        appendLine(out, repeat('-', LINE_WIDTH));
        if (!template.phone().isBlank()) {
            appendLine(out, template.phone());
        }
        appendLine(out, capitalize(DateTimeFormatter.ofPattern("EEEE dd MMM yyyy HH:mm", Locale.FRENCH)
            .format(LocalDateTime.now())));
        setBold(out, true);
        appendLine(out, orderId > 0 ? template.ticketPrefix() + " " + orderId : template.ticketPrefix());
        setBold(out, false);
        appendLine(out, "");

        setAlign(out, 0);
        for (OrderLine line : order.getLines()) {
            String left = line.getQuantity() + " " + safeUpper(line.getProduct().getName());
            String right = formatAmount(line.getLineTotal(), template.currencyLabel());
            appendLine(out, leftRight(left, right));
            if (line.getTags() != null && !line.getTags().isEmpty()) {
                for (com.cafepos.model.Tag tag : line.getTags()) {
                    String sign = tag.getPriceModifier() >= 0 ? "+ " : "- ";
                    appendLine(out, leftRight(
                            "  " + sign + safeUpper(tag.getName()),
                            formatAmount(Math.abs(tag.getPriceModifier()), template.currencyLabel())
                    ));
                }
            }
        }

        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, leftRight("SOUS-TOTAL", formatAmount(order.getSubtotal(), template.currencyLabel())));
        if (order.hasDiscount()) {
            appendLine(out, leftRight("REMISE", "-" + formatAmount(order.getAppliedDiscountAmount(), template.currencyLabel())));
        }
        if (order.getTvaPercent() > 0) {
            appendLine(out, leftRight(
                    "TVA (" + formatPercent(order.getTvaPercent()) + "%)",
                    formatAmount(order.getTvaAmount(), template.currencyLabel())
            ));
        }

        appendLine(out, repeat('=', LINE_WIDTH));
        setBold(out, true);
        appendLine(out, leftRight("TOTAL", formatAmount(order.getTotal(), template.currencyLabel())));
        setBold(out, false);

        appendPaymentBlock(out, order, remainingBalance, template.currencyLabel());
        appendCustomerBlock(out, order, remainingBalance, template.currencyLabel(), template.showCustomerBlock());

        setAlign(out, 1);
        appendLine(out, repeat(template.separatorChar(), LINE_WIDTH));
        if (!template.footer().isBlank()) {
            appendLine(out, template.footer());
        }

        setAlign(out, 0);
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        // Coupe papier GS V 0 (si supportee par l'imprimante).
        appendCommand(out, 0x1D, 0x56, 0x00);
        return out.toByteArray();
    }

    private byte[] buildInvoiceTicket(Order order,
                                      int orderId,
                                      String invoiceNumber,
                                      String recipientName,
                                      String recipientAddress) {
        ReceiptTemplate template = loadTemplate();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendCommand(out, 0x1B, 0x40); // ESC @

        setAlign(out, 1);
        setBold(out, true);
        appendLine(out, "COMMON GROUNDS");
        setBold(out, false);
        appendLine(out, "Facture N°: " + resolveInvoiceNumber(orderId, invoiceNumber));
        appendLine(out, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH)
            .format(LocalDateTime.now()));
        if (recipientName != null && !recipientName.isBlank()) {
            appendLine(out, "Destinataire: " + recipientName.trim());
        }
        if (recipientAddress != null && !recipientAddress.isBlank()) {
            appendLine(out, recipientAddress.trim());
        }

        setAlign(out, 0);
        appendLine(out, repeat('-', LINE_WIDTH));

        for (OrderLine line : order.getLines()) {
            String left = line.getQuantity() + " x " + safeUpper(line.getProduct().getName())
                    + " (PU " + formatAmount(line.getUnitTotal(), template.currencyLabel()) + ")";
            String right = formatAmount(line.getLineTotal(), template.currencyLabel());
            appendLine(out, leftRight(left, right));
        }

        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, leftRight("SOUS-TOTAL", formatAmount(order.getSubtotal(), template.currencyLabel())));
        if (order.getTvaPercent() > 0) {
            appendLine(out, leftRight(
                    "TVA (" + formatPercent(order.getTvaPercent()) + "%)",
                    formatAmount(order.getTvaAmount(), template.currencyLabel())
            ));
        }
        if (order.hasDiscount()) {
            appendLine(out, leftRight("REMISE", "-" + formatAmount(order.getAppliedDiscountAmount(), template.currencyLabel())));
        }

        appendLine(out, repeat('=', LINE_WIDTH));
        setBold(out, true);
        appendLine(out, leftRight("TOTAL", formatAmount(order.getTotal(), template.currencyLabel())));
        setBold(out, false);

        setAlign(out, 1);
        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, "Merci de votre visite!");

        setAlign(out, 0);
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        appendLine(out, "");
        // Coupe papier GS V 0 (si supportee par l'imprimante).
        appendCommand(out, 0x1D, 0x56, 0x00);
        return out.toByteArray();
    }

    /** Ticket recapitulatif imprime a la cloture de session (fin de journee). */
    public java.util.List<String> buildEndOfDaySummaryTextLines(SalesSummary summary, java.time.LocalDate day) {
        ReceiptTemplate template = loadTemplate();
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(center("COMMON GROUNDS"));
        lines.add(center("CLOTURE DE SESSION"));
        lines.add(repeat('-', LINE_WIDTH));
        lines.add(center(capitalize(DateTimeFormatter.ofPattern("EEEE dd MMM yyyy", Locale.FRENCH).format(day))));
        lines.add(center(capitalize(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)))));
        lines.add(repeat('=', LINE_WIDTH));
        lines.add(leftRight("NB COMMANDES", String.valueOf(summary.orderCount())));
        lines.add(leftRight("CHIFFRE D'AFFAIRES", formatAmount(summary.total(), template.currencyLabel())));
        lines.add(repeat('-', LINE_WIDTH));
        lines.add(leftRight("ESPECES", formatAmount(summary.cashTotal(), template.currencyLabel())));
        lines.add(leftRight("PREPAYE", formatAmount(summary.prepaidTotal(), template.currencyLabel())));
        lines.add(repeat('-', LINE_WIDTH));
        lines.add(leftRight("COUT INGREDIENTS", formatAmount(summary.ingredientCost(), template.currencyLabel())));
        lines.add(leftRight("MARGE BRUTE", formatAmount(summary.grossProfit(), template.currencyLabel())));
        lines.add(leftRight("RETRAITS CAISSE", formatAmount(summary.cashWithdrawals(), template.currencyLabel())));
        lines.add(repeat('=', LINE_WIDTH));
        lines.add(leftRight("REVENU NET", formatAmount(summary.netRevenue(), template.currencyLabel())));
        lines.add(center(repeat(template.separatorChar(), LINE_WIDTH)));
        if (!template.footer().isBlank()) {
            lines.add(center(template.footer()));
        }
        return lines;
    }

    public java.util.List<String> buildTestReceiptTextLines() {
        ReceiptTemplate template = loadTemplate();
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(center("COMMON GROUNDS"));
        lines.add(center("American Institute, Alger"));
        lines.add(repeat('-', LINE_WIDTH));
        if (!template.phone().isBlank()) {
            lines.add(center(template.phone()));
        }
        lines.add(center("TEST IMPRESSION"));
        lines.add(center(capitalize(LocalDateTime.now().format(DATE_FORMAT))));
        lines.add(repeat('=', LINE_WIDTH));
        lines.add(leftRight("TOTAL", formatAmount(0, template.currencyLabel())));
        lines.add(center(repeat(template.separatorChar(), LINE_WIDTH)));
        if (!template.footer().isBlank()) {
            lines.add(center(template.footer()));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Apercu / impression sur imprimante standard (non thermique)
    // ------------------------------------------------------------------
    // Les methodes ci-dessus produisent des octets ESC/POS bruts, compris
    // uniquement par une imprimante ticket (thermique). Envoyer ce flux a une
    // imprimante bureautique classique (jet d'encre/laser type Epson) ne
    // produit rien d'exploitable : ces pilotes n'interpretent pas les
    // commandes ESC/POS. Les methodes suivantes reconstruisent le meme
    // contenu en texte brut, imprimable sur n'importe quelle imprimante via
    // l'API Java standard (java.awt.print), et servent aussi a l'apercu.

    public java.util.List<String> buildReceiptTextLines(Order order, int orderId, double remainingBalance) {
        ReceiptTemplate template = loadTemplate();
        java.util.List<String> lines = new java.util.ArrayList<>();

        lines.add(center("COMMON GROUNDS"));
        lines.add(center("American Institute, Alger"));
        lines.add(repeat('-', LINE_WIDTH));
        if (!template.phone().isBlank()) {
            lines.add(center(template.phone()));
        }
        lines.add(center(capitalize(DateTimeFormatter.ofPattern("EEEE dd MMM yyyy HH:mm", Locale.FRENCH)
                .format(LocalDateTime.now()))));
        lines.add(center(orderId > 0 ? template.ticketPrefix() + " " + orderId : template.ticketPrefix()));
        lines.add("");

        for (OrderLine line : order.getLines()) {
            String left = line.getQuantity() + " " + safeUpper(line.getProduct().getName());
            String right = formatAmount(line.getLineTotal(), template.currencyLabel());
            lines.add(leftRight(left, right));
            if (line.getTags() != null && !line.getTags().isEmpty()) {
                for (com.cafepos.model.Tag tag : line.getTags()) {
                    String sign = tag.getPriceModifier() >= 0 ? "+ " : "- ";
                    lines.add(leftRight(
                            "  " + sign + safeUpper(tag.getName()),
                            formatAmount(Math.abs(tag.getPriceModifier()), template.currencyLabel())
                    ));
                }
            }
        }

        lines.add(repeat('-', LINE_WIDTH));
        lines.add(leftRight("SOUS-TOTAL", formatAmount(order.getSubtotal(), template.currencyLabel())));
        if (order.hasDiscount()) {
            lines.add(leftRight("REMISE", "-" + formatAmount(order.getAppliedDiscountAmount(), template.currencyLabel())));
        }
        if (order.getTvaPercent() > 0) {
            lines.add(leftRight(
                    "TVA (" + formatPercent(order.getTvaPercent()) + "%)",
                    formatAmount(order.getTvaAmount(), template.currencyLabel())
            ));
        }
        lines.add(repeat('=', LINE_WIDTH));
        lines.add(leftRight("TOTAL", formatAmount(order.getTotal(), template.currencyLabel())));

        appendPaymentBlockText(lines, order, remainingBalance, template.currencyLabel());
        appendCustomerBlockText(lines, order, remainingBalance, template.currencyLabel(), template.showCustomerBlock());

        lines.add(center(repeat(template.separatorChar(), LINE_WIDTH)));
        if (!template.footer().isBlank()) {
            lines.add(center(template.footer()));
        }
        return lines;
    }

    public java.util.List<String> buildInvoiceTextLines(Order order,
                                                         int orderId,
                                                         String invoiceNumber,
                                                         String recipientName,
                                                         String recipientAddress) {
        ReceiptTemplate template = loadTemplate();
        java.util.List<String> lines = new java.util.ArrayList<>();

        lines.add(center("COMMON GROUNDS"));
        lines.add(center("Facture N°: " + resolveInvoiceNumber(orderId, invoiceNumber)));
        lines.add(center(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH).format(LocalDateTime.now())));
        if (recipientName != null && !recipientName.isBlank()) {
            lines.add(center("Destinataire: " + recipientName.trim()));
        }
        if (recipientAddress != null && !recipientAddress.isBlank()) {
            lines.add(center(recipientAddress.trim()));
        }
        lines.add(repeat('-', LINE_WIDTH));

        for (OrderLine line : order.getLines()) {
            String left = line.getQuantity() + " x " + safeUpper(line.getProduct().getName())
                    + " (PU " + formatAmount(line.getUnitTotal(), template.currencyLabel()) + ")";
            String right = formatAmount(line.getLineTotal(), template.currencyLabel());
            lines.add(leftRight(left, right));
        }

        lines.add(repeat('-', LINE_WIDTH));
        lines.add(leftRight("SOUS-TOTAL", formatAmount(order.getSubtotal(), template.currencyLabel())));
        if (order.getTvaPercent() > 0) {
            lines.add(leftRight(
                    "TVA (" + formatPercent(order.getTvaPercent()) + "%)",
                    formatAmount(order.getTvaAmount(), template.currencyLabel())
            ));
        }
        if (order.hasDiscount()) {
            lines.add(leftRight("REMISE", "-" + formatAmount(order.getAppliedDiscountAmount(), template.currencyLabel())));
        }
        lines.add(repeat('=', LINE_WIDTH));
        lines.add(leftRight("TOTAL", formatAmount(order.getTotal(), template.currencyLabel())));
        lines.add(repeat('-', LINE_WIDTH));
        lines.add(center("Merci de votre visite!"));
        return lines;
    }

    private void appendPaymentBlockText(java.util.List<String> lines, Order order, double remainingBalance, String currency) {
        PaymentType type = order.getPaymentType() == null ? PaymentType.ESPECES : order.getPaymentType();
        lines.add("");
        switch (type) {
            case ESPECES -> lines.add(leftRight("ESPECES", formatAmount(order.getTotal(), currency)));
            case PREPAYE -> lines.add(leftRight("CARTE PREPAYEE", formatAmount(order.getTotal(), currency)));
            case MIXTE -> {
                double prepaid = order.getPrepaidAmount() > 0 ? order.getPrepaidAmount() : 0;
                double cash = order.getCashAmount() > 0 ? order.getCashAmount() : Math.max(0, order.getTotal() - prepaid);
                lines.add(leftRight("CARTE PREPAYEE", formatAmount(prepaid, currency)));
                lines.add(leftRight("ESPECES", formatAmount(cash, currency)));
            }
        }
        if ((type == PaymentType.PREPAYE || type == PaymentType.MIXTE) && remainingBalance >= 0) {
            lines.add(leftRight("SOLDE RESTANT", formatAmount(remainingBalance, currency)));
        }
    }

    private void appendCustomerBlockText(java.util.List<String> lines,
                                         Order order,
                                         double remainingBalance,
                                         String currency,
                                         boolean showCustomerBlock) {
        if (!showCustomerBlock) {
            return;
        }
        Customer customer = order.getCustomer();
        if (customer == null) {
            return;
        }
        lines.add(repeat('-', LINE_WIDTH));
        lines.add("CLIENT       : " + safeUpper(customer.getName()));
        lines.add("NUM CARTE    : " + safeUpper(customer.getCardUid()));
        lines.add(leftRight("ANCIEN SOLDE", formatAmount(customer.getBalance(), currency)));

        double prepaidAmount = switch (order.getPaymentType()) {
            case PREPAYE -> order.getTotal();
            case MIXTE -> Math.max(0, order.getPrepaidAmount());
            default -> 0;
        };
        if (prepaidAmount > 0) {
            lines.add(leftRight("MONTANT", formatAmount(prepaidAmount, currency)));
            lines.add(leftRight("NOUVEAU SOLDE", formatAmount(remainingBalance, currency)));
        }
    }

    private String center(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() >= LINE_WIDTH) {
            return text;
        }
        int totalPad = LINE_WIDTH - text.length();
        int left = totalPad / 2;
        return repeat(' ', left) + text;
    }

    /**
     * Imprime des lignes de texte brut sur une imprimante Windows standard
     * (jet d'encre/laser) via l'API d'impression Java classique — a
     * l'inverse de printPayload() qui envoie des octets ESC/POS compris
     * seulement par une imprimante ticket thermique.
     */
    public void printPlainText(java.util.List<String> lines, String printerName) throws Exception {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Rien a imprimer");
        }
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        if (printerName != null && !printerName.isBlank()) {
            PrintService target = resolvePrintServiceByName(printerName);
            if (target == null) {
                throw new IllegalStateException("Imprimante introuvable: " + printerName);
            }
            job.setPrintService(target);
        }
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            int linesPerPage = 60;
            int startLine = pageIndex * linesPerPage;
            if (startLine >= lines.size()) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics;
            java.awt.Font font = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10);
            g2.setFont(font);
            java.awt.FontMetrics metrics = g2.getFontMetrics();
            int lineHeight = metrics.getHeight();
            int x = (int) pageFormat.getImageableX();
            int y = (int) pageFormat.getImageableY() + metrics.getAscent();
            int endLine = Math.min(startLine + linesPerPage, lines.size());
            for (int i = startLine; i < endLine; i++) {
                g2.drawString(lines.get(i), x, y);
                y += lineHeight;
            }
            return java.awt.print.Printable.PAGE_EXISTS;
        });
        job.print();
    }

    private PrintService resolvePrintServiceByName(String name) {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(name)) {
                return service;
            }
        }
        return null;
    }

    private String resolveInvoiceNumber(int orderId, String invoiceNumber) {
        if (invoiceNumber != null && !invoiceNumber.isBlank()) {
            return invoiceNumber.trim();
        }
        String datePart = java.time.LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = orderId > 0 ? Math.abs(orderId % 10_000) : (int) (System.currentTimeMillis() % 10_000);
        return datePart + "-" + String.format("%04d", suffix);
    }

    private byte[] buildTestReceipt() {
        ReceiptTemplate template = loadTemplate();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendCommand(out, 0x1B, 0x40);
        appendCommand(out, 0x1B, 0x74, 0x02); // ESC t 2 = codepage CP850
        setAlign(out, 1);
        setBold(out, true);
        appendLine(out, "COMMON GROUNDS");
        setBold(out, false);
        appendLine(out, "American Institute, Alger");
        appendLine(out, repeat('-', LINE_WIDTH));
        if (!template.phone().isBlank()) {
            appendLine(out, template.phone());
        }
        appendLine(out, "TEST IMPRESSION");
        appendLine(out, capitalize(LocalDateTime.now().format(DATE_FORMAT)));
        appendLine(out, repeat('=', LINE_WIDTH));
        appendLine(out, leftRight("TOTAL", formatAmount(0, template.currencyLabel())));
        appendLine(out, repeat(template.separatorChar(), LINE_WIDTH));
        if (!template.footer().isBlank()) {
            appendLine(out, template.footer());
        }
        appendLine(out, "");
        appendLine(out, "");
        appendCommand(out, 0x1D, 0x56, 0x00);
        return out.toByteArray();
    }

    private void appendPaymentBlock(ByteArrayOutputStream out, Order order, double remainingBalance, String currency) {
        PaymentType type = order.getPaymentType() == null ? PaymentType.ESPECES : order.getPaymentType();
        appendLine(out, "");
        switch (type) {
            case ESPECES -> appendLine(out, leftRight("ESPECES", formatAmount(order.getTotal(), currency)));
            case PREPAYE -> appendLine(out, leftRight("CARTE PREPAYEE", formatAmount(order.getTotal(), currency)));
            case MIXTE -> {
                double prepaid = order.getPrepaidAmount() > 0 ? order.getPrepaidAmount() : 0;
                double cash = order.getCashAmount() > 0 ? order.getCashAmount() : Math.max(0, order.getTotal() - prepaid);
                appendLine(out, leftRight("CARTE PREPAYEE", formatAmount(prepaid, currency)));
                appendLine(out, leftRight("ESPECES", formatAmount(cash, currency)));
            }
        }
        if (type == PaymentType.PREPAYE || type == PaymentType.MIXTE) {
            if (remainingBalance >= 0) {
                appendLine(out, leftRight("SOLDE RESTANT", formatAmount(remainingBalance, currency)));
            }
        }
    }

    private void appendCustomerBlock(ByteArrayOutputStream out,
                                     Order order,
                                     double remainingBalance,
                                     String currency,
                                     boolean showCustomerBlock) {
        if (!showCustomerBlock) {
            return;
        }
        Customer customer = order.getCustomer();
        if (customer == null) {
            return;
        }

        appendLine(out, repeat('-', LINE_WIDTH));
        appendLine(out, "CLIENT       : " + safeUpper(customer.getName()));
        appendLine(out, "NUM CARTE    : " + safeUpper(customer.getCardUid()));
        appendLine(out, leftRight("ANCIEN SOLDE", formatAmount(customer.getBalance(), currency)));

        double prepaidAmount = switch (order.getPaymentType()) {
            case PREPAYE -> order.getTotal();
            case MIXTE -> Math.max(0, order.getPrepaidAmount());
            default -> 0;
        };
        if (prepaidAmount > 0) {
            appendLine(out, leftRight("MONTANT", formatAmount(prepaidAmount, currency)));
            appendLine(out, leftRight("NOUVEAU SOLDE", formatAmount(remainingBalance, currency)));
        }
    }

    /** Remplace les caractères de dessin de boîtes Unicode par des équivalents ASCII sûrs pour CP850. */
    private static String sanitizeReceiptTemplate(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u2500', '-').replace('\u2550', '=').replace('\u2501', '-')
                .replace('\u2502', '|').replace('\u2503', '|').replace('\u2551', '|')
                .replace('\u2554', '+').replace('\u2557', '+').replace('\u255A', '+')
                .replace('\u255D', '+').replace('\u2560', '+').replace('\u2563', '+')
                .replace('\u2566', '+').replace('\u2569', '+').replace('\u256C', '+');
    }

    private ReceiptTemplate loadTemplate() {
        String storeName = readSetting(RECEIPT_STORE_NAME_KEY, DEFAULT_STORE_NAME);
        String phone = readSetting(RECEIPT_PHONE_KEY, DEFAULT_PHONE);
        String ticketPrefix = readSetting(RECEIPT_TICKET_PREFIX_KEY, DEFAULT_TICKET_PREFIX);
        String footer = readSetting(RECEIPT_FOOTER_KEY, DEFAULT_FOOTER);
        String currency = readSetting(RECEIPT_CURRENCY_KEY, DEFAULT_CURRENCY);
        String separator = readSetting(RECEIPT_SEPARATOR_KEY, DEFAULT_SEPARATOR);
        String showCustomerRaw = readSetting(RECEIPT_SHOW_CUSTOMER_KEY, "true");
        char separatorChar = separator == null || separator.isBlank() ? '*' : separator.charAt(0);
        boolean showCustomer = Boolean.parseBoolean(showCustomerRaw);

        return new ReceiptTemplate(
                sanitizeReceiptTemplate(safeLine(storeName)),
                safeLine(phone),
                safeLine(ticketPrefix),
                sanitizeReceiptTemplate(safeLine(footer)),
                safeLine(currency),
                separatorChar,
                showCustomer
        );
    }

    private String readSetting(String key, String fallback) {
        try {
            String value = settingsDAO.getValue(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String formatAmount(double amount, String currency) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        String suffix = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.toUpperCase(Locale.ROOT);
        return format.format(amount) + " " + suffix;
    }

    private String formatPercent(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String safeLine(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeUpper(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private void appendLine(ByteArrayOutputStream out, String text) {
        try {
            byte[] bytes = (text + "\n").getBytes("CP850");
            out.writeBytes(bytes);
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback to ASCII if somehow CP850 not available
            byte[] bytes = (text + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            out.writeBytes(bytes);
        }
    }

    private void appendCommand(ByteArrayOutputStream out, int... bytes) {
        for (int value : bytes) {
            out.write(value);
        }
    }

    private void setAlign(ByteArrayOutputStream out, int mode) {
        appendCommand(out, 0x1B, 0x61, mode); // ESC a n
    }

    private void setBold(ByteArrayOutputStream out, boolean enabled) {
        appendCommand(out, 0x1B, 0x45, enabled ? 1 : 0); // ESC E n
    }

    private String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private String leftRight(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        if (safeLeft.length() + safeRight.length() >= LINE_WIDTH) {
            return safeLeft + " " + safeRight;
        }
        int spaces = LINE_WIDTH - safeLeft.length() - safeRight.length();
        StringBuilder sb = new StringBuilder(LINE_WIDTH);
        sb.append(safeLeft);
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        sb.append(safeRight);
        return sb.toString();
    }

    private void logError(Exception ex) {
        try {
            Path logPath = getLogPath();
            Files.createDirectories(logPath.getParent());
            String line = LocalDateTime.now().format(DATE_FORMAT) + " - " + ex.getMessage() + "\n";
            Files.writeString(logPath, line, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Pas d'echec si le journal ne peut pas etre ecrit.
        }
    }

    private Path getLogPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "logs", "print_errors.log");
        }
        return Paths.get(appData, "CafePOS", "logs", "print_errors.log");
    }

    private record ReceiptTemplate(
            String storeName,
            String phone,
            String ticketPrefix,
            String footer,
            String currencyLabel,
            char separatorChar,
            boolean showCustomerBlock
    ) {
    }
}
